package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import com.erikferreira.stocksync.service.OrderService;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.SyncStatus;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.integration.dto.ExternalOrderItemDTO;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.repository.OrderRepository;
import com.erikferreira.stocksync.repository.StockMovementRepository;
import com.erikferreira.stocksync.repository.SyncEventRepository;
import com.erikferreira.stocksync.service.InventoryService;
import com.erikferreira.stocksync.service.MarketplaceListingService;
import com.erikferreira.stocksync.service.OrderSynchronizationService;
import com.erikferreira.stocksync.service.ProductService;
import com.erikferreira.stocksync.service.SalesChannelService;
import com.erikferreira.stocksync.service.StockMovementService;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class CoreWorkflowIntegrationTest extends PostgreSQLIntegrationTest {
    @Autowired private OrderService orderService;

    @Test
    void manualOrderShouldReturnItemsAndCancellationRestoreStock() {
        String suffix = String.valueOf(System.nanoTime());
        var product = productService.insert(new ProductInsertDTO("MANUAL-" + suffix, "Manual product", null, BigDecimal.TEN, 10, 0));
        var channel = salesChannelService.insert(new SalesChannelRequestDTO("Manual-" + suffix, ChannelType.OTHER, null));
        var created = orderService.insert(new OrderInsertDTO("MANUAL-" + suffix, channel.id(), Instant.now(),
                List.of(new OrderItemInsertDTO(product.id(), 2, new BigDecimal("12.50")))));
        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(product.id());
            assertThat(item.subtotal()).isEqualByComparingTo("25.00");
        });
        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(10);
        assertThat(created.stockDeducted()).isFalse();
        var paid = orderService.confirmPayment(created.id());
        assertThat(paid.status()).isEqualTo(com.erikferreira.stocksync.entity.enums.OrderStatus.PAID);
        assertThat(paid.stockDeducted()).isTrue();
        orderService.confirmPayment(created.id());
        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(8);
        orderService.cancelOrder(created.id());
        orderService.cancelOrder(created.id());
        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(10);
    }

    @Test
    void orderPageShouldKeepRequestedDescendingOrderWithAllItems() {
        String suffix = String.valueOf(System.nanoTime());
        var product = productService.insert(new ProductInsertDTO("PAGE-" + suffix, "Page product", null, BigDecimal.TEN, 10, 0));
        var channel = salesChannelService.insert(new SalesChannelRequestDTO("Page-" + suffix, ChannelType.OTHER, null));
        for (int i = 0; i < 3; i++) orderService.insert(new OrderInsertDTO("PAGE-" + suffix + "-" + i,
                channel.id(), Instant.now(), List.of(new OrderItemInsertDTO(product.id(), 1, BigDecimal.TEN))));
        var page = orderService.findAllPaged(PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "id")));
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent()).extracting(item -> item.id()).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(page.getContent()).allSatisfy(order -> assertThat(order.items()).hasSize(1));
    }

    @MockitoBean
    private MarketplaceIntegrationPort marketplaceIntegrationPort;

    @Autowired
    private ProductService productService;

    @Autowired
    private SalesChannelService salesChannelService;

    @Autowired
    private MarketplaceListingService listingService;

    @Autowired
    private StockMovementService movementService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderSynchronizationService orderSynchronizationService;

    @Autowired
    private MarketplaceListingRepository listingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private SyncEventRepository syncEventRepository;

    @Test
    void localStockChangeShouldSynchronizeActiveMercadoLivreListing() {
        String suffix = String.valueOf(System.nanoTime());
        var product = productService.insert(new ProductInsertDTO("SKU-LOCAL-" + suffix, "Local product", null, BigDecimal.TEN, 10, 2));

        var channel = salesChannelService.insert(new SalesChannelRequestDTO("ML-LOCAL-" + suffix, ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com"));

        listingService.insert(new MarketplaceListingInsertDTO(product.id(), channel.id(), "MLB-LOCAL-" + suffix, null, ListingStatus.ACTIVE));

        movementService.registerMovement(new StockMovementInsertDTO(product.id(), 3, MovementType.MANUAL_DECREASE, null, null, "local sale"));

        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(7);
        verify(marketplaceIntegrationPort).updateStock("MLB-LOCAL-" + suffix, 7);
        assertThat(listingRepository.findByListingIdAndSalesChannelId("MLB-LOCAL-" + suffix, channel.id())).get().extracting(MarketplaceListing::getLastSyncedAt).isNotNull();
    }

    @Test
    void mercadoLivreOrderShouldCreateOrderDecreaseStockAndSynchronizeNewQuantity() {
        String suffix = String.valueOf(System.nanoTime());

        var product = productService.insert(new ProductInsertDTO("SKU-ORDER-" + suffix, "Order product", null, new BigDecimal("25.00"), 10, 2));

        var channel = salesChannelService.insert(new SalesChannelRequestDTO("ML-ORDER-" + suffix, ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com"));

        String listingId = "MLB-ORDER-" + suffix;
        String externalOrderId = "EXT-" + suffix;

        listingService.insert(new MarketplaceListingInsertDTO(product.id(), channel.id(), listingId, null, ListingStatus.ACTIVE));

        when(marketplaceIntegrationPort.fetchNewOrders(channel.id())).thenReturn(List.of(new ExternalOrderDTO(externalOrderId, Instant.now(), "paid", List.of(new ExternalOrderItemDTO(listingId, 2, new BigDecimal("25.00"))))));

        orderSynchronizationService.synchronizeOrders(channel.id());

        assertThat(orderRepository.existsBySalesChannelIdAndExternalOrderId(channel.id(), externalOrderId)).isTrue();
        var imported = orderRepository.findAll().stream().filter(o -> externalOrderId.equals(o.getExternalOrderId())).findFirst().orElseThrow();
        assertThat(imported.getStatus()).isEqualTo(com.erikferreira.stocksync.entity.enums.OrderStatus.PAID);
        assertThat(imported.isStockDeducted()).isTrue();
        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(8);
        assertThat(movementService.findHistoryByProduct(product.id(), PageRequest.of(0, 10)).getContent()).singleElement().satisfies(movement -> assertThat(movement.type()).isEqualTo(MovementType.SALE));
        verify(marketplaceIntegrationPort).updateStock(listingId, 8);
    }

    @Test
    void orderWithInsufficientStockShouldRollbackAndRegisterFailureEvent() {
        String suffix = String.valueOf(System.nanoTime());

        var product = productService.insert(new ProductInsertDTO("SKU-ROLLBACK-" + suffix, "Rollback product", null, BigDecimal.TEN, 1, 0));

        var channel = salesChannelService.insert(new SalesChannelRequestDTO("ML-ROLLBACK-" + suffix, ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com"));

        String listingId = "MLB-ROLLBACK-" + suffix;
        String externalOrderId = "EXT-ROLLBACK-" + suffix;

        listingService.insert(new MarketplaceListingInsertDTO(product.id(), channel.id(), listingId, null, ListingStatus.ACTIVE));

        when(marketplaceIntegrationPort.fetchNewOrders(channel.id())).thenReturn(List.of(new ExternalOrderDTO(externalOrderId, Instant.now(), "paid", List.of(new ExternalOrderItemDTO(listingId, 2, BigDecimal.TEN)))));

        orderSynchronizationService.synchronizeOrders(channel.id());

        assertThat(orderRepository.existsBySalesChannelIdAndExternalOrderId(channel.id(), externalOrderId)).isFalse();
        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(1);
        assertThat(movementRepository.findByProductId(product.id(), PageRequest.of(0, 10))).isEmpty();
        assertThat(syncEventRepository.findByStatus(SyncStatus.FAILURE, PageRequest.of(0, 10)).getContent()).anySatisfy(event -> {
            assertThat(event.getExternalOrderId()).isEqualTo(externalOrderId);
            assertThat(event.getErrorMessage()).contains("Insufficient stock");
        });
    }
}
