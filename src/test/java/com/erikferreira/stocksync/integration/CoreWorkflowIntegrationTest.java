package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementInsertDTO;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class CoreWorkflowIntegrationTest extends PostgreSQLIntegrationTest {

    @MockitoBean
    private MarketplaceIntegrationPort marketplaceIntegrationPort;

    @Autowired private ProductService productService;
    @Autowired private SalesChannelService salesChannelService;
    @Autowired private MarketplaceListingService listingService;
    @Autowired private StockMovementService movementService;
    @Autowired private InventoryService inventoryService;
    @Autowired private OrderSynchronizationService orderSynchronizationService;
    @Autowired private MarketplaceListingRepository listingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private StockMovementRepository movementRepository;
    @Autowired private SyncEventRepository syncEventRepository;

    @Test
    void localStockChangeShouldSynchronizeActiveMercadoLivreListing() {
        String suffix = String.valueOf(System.nanoTime());
        var product = productService.insert(new ProductInsertDTO(
                "SKU-LOCAL-" + suffix, "Local product", null, BigDecimal.TEN, 10, 2));
        var channel = salesChannelService.insert(new SalesChannelRequestDTO(
                "ML-LOCAL-" + suffix, ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com"));
        listingService.insert(new MarketplaceListingInsertDTO(
                product.id(), channel.id(), "MLB-LOCAL-" + suffix, null, ListingStatus.ACTIVE));

        movementService.registerMovement(new StockMovementInsertDTO(
                product.id(), 3, MovementType.MANUAL_DECREASE, null, null, "local sale"));

        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(7);
        verify(marketplaceIntegrationPort).updateStock("MLB-LOCAL-" + suffix, 7);
        assertThat(listingRepository.findByListingIdAndSalesChannelId("MLB-LOCAL-" + suffix, channel.id()))
                .get().extracting(listing -> listing.getLastSyncedAt()).isNotNull();
    }

    @Test
    void mercadoLivreOrderShouldCreateOrderDecreaseStockAndSynchronizeNewQuantity() {
        String suffix = String.valueOf(System.nanoTime());
        var product = productService.insert(new ProductInsertDTO(
                "SKU-ORDER-" + suffix, "Order product", null, new BigDecimal("25.00"), 10, 2));
        var channel = salesChannelService.insert(new SalesChannelRequestDTO(
                "ML-ORDER-" + suffix, ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com"));
        String listingId = "MLB-ORDER-" + suffix;
        String externalOrderId = "EXT-" + suffix;
        listingService.insert(new MarketplaceListingInsertDTO(
                product.id(), channel.id(), listingId, null, ListingStatus.ACTIVE));
        when(marketplaceIntegrationPort.fetchNewOrders(channel.id())).thenReturn(List.of(
                new ExternalOrderDTO(externalOrderId, LocalDateTime.now(), "paid", List.of(
                        new ExternalOrderItemDTO(listingId, 2, new BigDecimal("25.00"))))));

        orderSynchronizationService.synchronizeOrders(channel.id());

        assertThat(orderRepository.existsBySalesChannelIdAndExternalOrderId(channel.id(), externalOrderId)).isTrue();
        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(8);
        assertThat(movementService.findHistoryByProduct(product.id(), PageRequest.of(0, 10)).getContent())
                .singleElement().satisfies(movement -> assertThat(movement.type()).isEqualTo(MovementType.SALE));
        verify(marketplaceIntegrationPort).updateStock(listingId, 8);
    }

    @Test
    void orderWithInsufficientStockShouldRollbackAndRegisterFailureEvent() {
        String suffix = String.valueOf(System.nanoTime());
        var product = productService.insert(new ProductInsertDTO(
                "SKU-ROLLBACK-" + suffix, "Rollback product", null, BigDecimal.TEN, 1, 0));
        var channel = salesChannelService.insert(new SalesChannelRequestDTO(
                "ML-ROLLBACK-" + suffix, ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com"));
        String listingId = "MLB-ROLLBACK-" + suffix;
        String externalOrderId = "EXT-ROLLBACK-" + suffix;
        listingService.insert(new MarketplaceListingInsertDTO(
                product.id(), channel.id(), listingId, null, ListingStatus.ACTIVE));
        when(marketplaceIntegrationPort.fetchNewOrders(channel.id())).thenReturn(List.of(
                new ExternalOrderDTO(externalOrderId, LocalDateTime.now(), "paid", List.of(
                        new ExternalOrderItemDTO(listingId, 2, BigDecimal.TEN)))));

        orderSynchronizationService.synchronizeOrders(channel.id());

        assertThat(orderRepository.existsBySalesChannelIdAndExternalOrderId(channel.id(), externalOrderId)).isFalse();
        assertThat(inventoryService.getAvailableQuantity(product.id())).isEqualTo(1);
        assertThat(movementRepository.findByProductId(product.id(), PageRequest.of(0, 10))).isEmpty();
        assertThat(syncEventRepository.findByStatus(SyncStatus.FAILURE, PageRequest.of(0, 10)).getContent())
                .anySatisfy(event -> {
                    assertThat(event.getExternalOrderId()).isEqualTo(externalOrderId);
                    assertThat(event.getErrorMessage()).contains("Insufficient stock");
                });
    }
}
