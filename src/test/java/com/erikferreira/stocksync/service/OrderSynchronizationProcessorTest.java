package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.order.OrderResponseDTO;
import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.enums.OrderStatus;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.integration.dto.ExternalOrderItemDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSynchronizationProcessorTest {

    @Mock private OrderService orderService;
    @Mock private MarketplaceListingService marketplaceListingService;
    @Mock private SyncEventService syncEventService;
    @InjectMocks private OrderSynchronizationProcessor processor;

    private MarketplaceListing listing;

    @BeforeEach
    void setUp() {
        Product product = ProductFactory.createProduct();
        listing = MarketplaceListing.builder().product(product).listingId("MLB123").build();
    }

    @Test
    void processOrderShouldMapItemsInsertOrderAndRegisterSuccess() {
        LocalDateTime orderDate = LocalDateTime.now();
        var externalOrder = new ExternalOrderDTO(
                "EXT-1", orderDate, "paid",
                List.of(new ExternalOrderItemDTO("MLB123", 2, new BigDecimal("15.00"))));
        when(marketplaceListingService.getByListingIdAndSalesChannelId("MLB123", 2L))
                .thenReturn(listing);
        when(orderService.insert(any(OrderInsertDTO.class))).thenReturn(new OrderResponseDTO(
                10L, 2L, "EXT-1", orderDate, OrderStatus.PENDING, List.of()));

        processor.processOrder(2L, externalOrder);

        ArgumentCaptor<OrderInsertDTO> captor = ArgumentCaptor.forClass(OrderInsertDTO.class);
        verify(orderService).insert(captor.capture());
        assertThat(captor.getValue().externalOrderId()).isEqualTo("EXT-1");
        assertThat(captor.getValue().items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(1L);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.unitPrice()).isEqualByComparingTo("15.00");
        });
        verify(syncEventService).registerOrderSuccess(2L, 10L, "EXT-1");
    }

    @Test
    void processOrderShouldNotInsertWhenListingCannotBeResolved() {
        var externalOrder = new ExternalOrderDTO(
                "EXT-1", LocalDateTime.now(), "paid",
                List.of(new ExternalOrderItemDTO("missing", 1, BigDecimal.TEN)));
        when(marketplaceListingService.getByListingIdAndSalesChannelId("missing", 2L))
                .thenThrow(new RuntimeException("listing missing"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.processOrder(2L, externalOrder))
                .isInstanceOf(RuntimeException.class);

        verify(orderService, never()).insert(any());
        verify(syncEventService, never()).registerOrderSuccess(any(), any(), any());
    }
}
