package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.entity.Order;
import com.erikferreira.stocksync.entity.OrderItem;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OrderStatus;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.repository.OrderItemRepository;
import com.erikferreira.stocksync.repository.OrderRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidOrderStatusException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductService productService;
    @Mock
    private OrderRepository repository;
    @Mock
    private SalesChannelService salesChannelService;
    @Mock
    private StockMovementService stockMovementService;
    @InjectMocks
    private OrderService service;

    private Product product;
    private SalesChannel channel;
    private Order order;
    private OrderItem item;

    @BeforeEach
    void setUp() {
        product = ProductFactory.createProduct();
        channel = SalesChannel.builder().id(2L).type(ChannelType.MERCADO_LIVRE).active(true).build();
        order = Order.builder().id(3L).salesChannel(channel).externalOrderId("ORDER-1")
                .orderDate(Instant.now()).status(OrderStatus.PENDING).build();
        item = OrderItem.builder().id(4L).order(order).product(product).quantity(2)
                .unitPrice(new BigDecimal("10.00")).build();
        order.setItems(List.of(item));
    }

    @Test
    void findByIdShouldMapOrderAndItems() {
        when(repository.findById(3L)).thenReturn(Optional.of(order));
        var result = service.findById(3L);
        assertThat(result.externalOrderId()).isEqualTo("ORDER-1");
        assertThat(result.items()).singleElement()
                .satisfies(dto -> assertThat(dto.subtotal()).isEqualByComparingTo("20.00"));
    }

    @Test
    void findAllPagedShouldFetchItemsAndKeepPagination() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(repository.fetchItemsForOrders(List.of(order))).thenReturn(List.of(order));
        var result = service.findAllPaged(pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).singleElement();
    }

    @Test
    void existsShouldDelegateToRepository() {
        when(repository.existsBySalesChannelIdAndExternalOrderId(2L, "ORDER-1")).thenReturn(true);
        assertThat(service.existsBySalesChannelIdAndExternalOrderId(2L, "ORDER-1")).isTrue();
    }

    @Test
    void insertShouldCreateItemsAndRegisterSaleMovement() {
        OrderInsertDTO dto = new OrderInsertDTO(
                "ORDER-1", 2L, order.getOrderDate(),
                List.of(new OrderItemInsertDTO(1L, 2, new BigDecimal("10.00"))));
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(productService.getProductEntityById(1L)).thenReturn(product);
        when(repository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(3L);
            return saved;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(item);
        when(repository.findById(3L)).thenReturn(Optional.of(order));

        var result = service.insert(dto);

        assertThat(result.id()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        ArgumentCaptor<StockMovementInsertDTO> movementCaptor = ArgumentCaptor.forClass(StockMovementInsertDTO.class);
        verify(stockMovementService).registerMovement(movementCaptor.capture());
        assertThat(movementCaptor.getValue().type()).isEqualTo(MovementType.SALE);
        assertThat(movementCaptor.getValue().originId()).isEqualTo(3L);
    }

    @Test
    void getOrderEntityShouldThrowWhenMissing() {
        when(repository.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOrderEntityById(3L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelOrderShouldSetCanceledAndRegisterMovements() {
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));

        var result = service.cancelOrder(3L);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELED);
        verify(repository).save(order);
        ArgumentCaptor<StockMovementInsertDTO> captor = ArgumentCaptor.forClass(StockMovementInsertDTO.class);
        verify(stockMovementService).registerMovement(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(MovementType.CANCELLATION);
    }

    @Test
    void cancelOrderShouldBeIdempotentWhenAlreadyCanceled() {
        order.setStatus(OrderStatus.CANCELED);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));

        assertThat(service.cancelOrder(3L).status()).isEqualTo(OrderStatus.CANCELED);

        verify(repository, never()).save(any());
        verify(stockMovementService, never()).registerMovement(any());
    }

    @Test
    void cancelOrderShouldRejectCompletedOrRefundedOrder() {
        order.setStatus(OrderStatus.COMPLETED);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(3L))
                .isInstanceOf(InvalidOrderStatusException.class);

        verify(repository, never()).save(any());
    }
}
