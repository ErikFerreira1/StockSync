package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OrderStatus;
import com.erikferreira.stocksync.repository.StockMovementRepository;
import com.erikferreira.stocksync.service.InventoryService;
import com.erikferreira.stocksync.service.OrderService;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.service.SalesChannelService;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.test.order-cancellation-concurrency=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderCancellationConcurrencyIntegrationTest extends PostgreSQLIntegrationTest {

    private static final int CONCURRENT_CANCELLATIONS = 10;

    @Autowired
    private ProductService productService;

    @Autowired
    private SalesChannelService salesChannelService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    void concurrentCancellationsShouldRestockOnlyOnce() throws Exception {
        long suffix = System.nanoTime();

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

        var order = orderService.insert(
                new OrderInsertDTO(
                        suffix + "",
                        salesChannel.id(),
                        Instant.now(),
                        List.of(
                                new OrderItemInsertDTO(
                                        product.id(),
                                        3,
                                        BigDecimal.TEN
                                )
                        )
                ));

        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(7);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CANCELLATIONS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_CANCELLATIONS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> operations = new ArrayList<>();

        try {
            for (int i = 0; i < CONCURRENT_CANCELLATIONS; i++) {
                operations.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent operations were not released in time");
                    }

                    orderService.cancelOrder(order.id());
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<?> operation : operations) {
                operation.get(10, TimeUnit.SECONDS);
            }

        } finally {
            executor.shutdownNow();
        }

        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(10);
        assertThat(orderService.findById(order.id()).status()).isEqualTo(OrderStatus.CANCELED);

        long cancellationMovements = stockMovementRepository
                .findByProductId(product.id(), PageRequest.of(0, 20))
                .getContent()
                .stream()
                .filter(movement -> movement.getType() == MovementType.CANCELLATION)
                .count();

        assertThat(cancellationMovements).isEqualTo(1);
    }

}
