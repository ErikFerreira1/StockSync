package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.entity.Order;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.SyncStatus;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.integration.dto.ExternalOrderItemDTO;
import com.erikferreira.stocksync.repository.OrderItemRepository;
import com.erikferreira.stocksync.repository.OrderRepository;
import com.erikferreira.stocksync.repository.StockMovementRepository;
import com.erikferreira.stocksync.repository.SyncEventRepository;
import com.erikferreira.stocksync.service.*;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.test.order-synchronization-concurrency=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OrderSynchronizationConcurrencyIntegrationTest extends PostgreSQLIntegrationTest {

    private static final int CONCURRENT_SYNCHRONIZATIONS = 2;

    @MockitoBean
    private MarketplaceIntegrationPort integrationPort;

    @MockitoSpyBean
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @Autowired
    private SalesChannelService salesChannelService;

    @Autowired
    private MarketplaceListingService marketplaceListingService;

    @Autowired
    private OrderSynchronizationService orderSynchronizationService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private SyncEventRepository syncEventRepository;

    @Test
    void concurrentSynchronizationsOfSameOrderShouldBeIdempotent() throws Exception{

        long suffix = System.nanoTime();
        String externalOrderId = "EXT-ORDER-SYNC-CONCURRENCY-" + suffix;
        String listingId = "MLB-ORDER-SYNC-CONCURRENCY-" + suffix;


        var product = productService.insert(
                new ProductInsertDTO(
                        "SKU-CONCURRENCY-" + suffix,
                        "Concurrency product",
                        null,
                        BigDecimal.TEN,
                        10,
                        0
                ));

        var salesChannel = salesChannelService.insert(
                new SalesChannelRequestDTO(
                        "mercadoLivre " + suffix,
                        ChannelType.MERCADO_LIVRE,
                        "https://www.mercadolivre.com.br"
                ));

        marketplaceListingService.insert(
                new MarketplaceListingInsertDTO(
                        product.id(),
                        salesChannel.id(),
                        listingId,
                        "https://www.mercadolivre.com.br",
                        ListingStatus.ACTIVE

                ));

        var externalOrderDTO = new ExternalOrderDTO(
                externalOrderId,
                Instant.now(),
                "paid",
                List.of(
                        new ExternalOrderItemDTO(
                                listingId,
                                2,
                                BigDecimal.TEN
                        )
                ));

        when(integrationPort.fetchNewOrders(salesChannel.id())).thenReturn(List.of(externalOrderDTO));

        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_SYNCHRONIZATIONS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_SYNCHRONIZATIONS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch checksReady = new CountDownLatch(CONCURRENT_SYNCHRONIZATIONS);
        CountDownLatch releaseChecks = new CountDownLatch(1);

        doAnswer(invocation -> {
            checksReady.countDown();

            boolean released = releaseChecks.await(5, TimeUnit.SECONDS);

            if (!released) {
                throw new IllegalStateException(
                        "Timeout awaiting releaseChecks"
                );
            }

            return invocation.callRealMethod();
        }).when(orderService)
                .existsBySalesChannelIdAndExternalOrderId(salesChannel.id(), externalOrderId);

        List<Future<?>> operations = new ArrayList<>();

        try {
            for (int i = 0; i < CONCURRENT_SYNCHRONIZATIONS; i++) {
                operations.add(executorService.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timeout awaiting start");
                    }


                    orderSynchronizationService.synchronizeOrders(salesChannel.id());
                    return null;
                }));
            }

            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(checksReady.await(5, TimeUnit.SECONDS)).isTrue();
            releaseChecks.countDown();

            for(Future<?> operation : operations) {
                operation.get(15, TimeUnit.SECONDS);
            }

        }
        finally {
            start.countDown();
            releaseChecks.countDown();
            executorService.shutdownNow();
        }


        List<Order> importedOrders = orderRepository.findAll().stream()
                .filter(order -> order.getSalesChannel().getId().equals(salesChannel.id()))
                .filter(order -> order.getExternalOrderId().equals(externalOrderId))
                .toList();

        assertThat(importedOrders).hasSize(1);
        Long importedOrderId = importedOrders.get(0).getId();

        assertThat(orderItemRepository.findAll())
                .filteredOn(item -> item.getOrder().getId().equals(importedOrderId))
                .hasSize(1);

        assertThat(stockMovementRepository.findByProductId(product.id(), PageRequest.of(0, 10)).getContent())
                .filteredOn(movement -> movement.getType() == MovementType.SALE)
                .hasSize(1);

        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(8);

        assertThat(syncEventRepository.findByStatus(SyncStatus.FAILURE, PageRequest.of(0, 100)).getContent())
                .filteredOn(event -> externalOrderId.equals(event.getExternalOrderId()))
                .isEmpty();

    }
}
