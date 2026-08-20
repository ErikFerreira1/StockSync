package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.order.OrderResponseDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemResponseDTO;
import com.erikferreira.stocksync.dto.stockMovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.entity.Order;
import com.erikferreira.stocksync.entity.OrderItem;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OrderStatus;
import com.erikferreira.stocksync.entity.enums.OriginType;
import com.erikferreira.stocksync.repository.OrderItemRepository;
import com.erikferreira.stocksync.repository.OrderRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidOrderStatusException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;


import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Validated
public class OrderService {

    private final OrderItemRepository orderItemRepository;
    private final ProductService productService;
    private final OrderRepository repository;
    private final SalesChannelService salesChannelService;
    private final StockMovementService stockMovementService;

    @Transactional(readOnly = true)
    public OrderResponseDTO findById(Long id) {
        Order order = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + id));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> findAllPaged(Pageable pageable) {
        Page<Order> page = repository.findAll(pageable);

        List<Order> ordersWithItems = repository.fetchItemsForOrders(page.getContent());

        return new PageImpl<>(
                ordersWithItems.stream().map(this::toResponse).toList(),
                pageable,
                page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public Order getOrderEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + id));
    }

    @Transactional(readOnly = true)
    public boolean existsBySalesChannelIdAndExternalOrderId(Long salesChannelId, String externalOrderId) {
        return repository.existsBySalesChannelIdAndExternalOrderId(salesChannelId, externalOrderId);
    }

    @Transactional
    public OrderResponseDTO insert(@Valid OrderInsertDTO dto) {
        SalesChannel channel = salesChannelService.getSalesChannelEntityById(dto.salesChannelId());

        Order order = new Order();
        copyDtoToEntity(dto, order, channel);
        repository.save(order);

        for (OrderItemInsertDTO item : dto.items()) {
            Long productId = item.productId();
            Integer quantity = item.quantity();
            BigDecimal unitPrice = item.unitPrice();

            Product product = productService.getProductEntityById(productId);

            StockMovementInsertDTO stockMovementInsertDTO = new StockMovementInsertDTO(
                    productId,
                    quantity,
                    MovementType.SALE,
                    OriginType.ORDER,
                    order.getId(),
                    "Sale from order " + order.getExternalOrderId());

            OrderItemResponseDTO orderItem = createOrderItem(product, order, quantity, unitPrice);

            stockMovementService.registerMovement(stockMovementInsertDTO);
        }
        Order savedOrder = repository.findById(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + order.getId()));


        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + orderId));

        if (order.getStatus() == OrderStatus.CANCELED) {
            return toResponse(order);
        }

        verifyOrderStatus(order);

        order.setStatus(OrderStatus.CANCELED);
        repository.save(order);

        for (OrderItem item : order.getItems()) {
            Long productId = item.getProduct().getId();
            Integer quantity = item.getQuantity();

            StockMovementInsertDTO stockMovementInsertDTO = new StockMovementInsertDTO(
                    productId,
                    quantity,
                    MovementType.CANCELLATION,
                    OriginType.ORDER,
                    order.getId(),
                    "Cancellation from order " + order.getExternalOrderId());

            stockMovementService.registerMovement(stockMovementInsertDTO);
        }

        return toResponse(order);
    }

    // helpers

    private void verifyOrderStatus(Order order) {
        switch (order.getStatus()) {
            case REFUNDED, COMPLETED ->
                    throw new InvalidOrderStatusException("This order cannot be cancelled, as it has already been: " + order.getStatus());
        }
    }

    private OrderItemResponseDTO createOrderItem(Product product, Order order, Integer quantity, BigDecimal unitPrice) {
        OrderItem orderItem = OrderItem.builder()
                .product(product)
                .order(order)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();

        return toItemResponseDTO(orderItemRepository.save(orderItem));
    }

    private void copyDtoToEntity(OrderInsertDTO dto, Order entity, SalesChannel channel) {
        entity.setSalesChannel(channel);
        entity.setExternalOrderId(dto.externalOrderId());
        entity.setOrderDate(dto.orderDate());
        entity.setStatus(OrderStatus.PENDING);
    }

    private OrderResponseDTO toResponse(Order order) {
        List<OrderItemResponseDTO> items = order.getItems().stream()
                .map(this::toItemResponseDTO)
                .toList();
        return new OrderResponseDTO(
                order.getId(),
                order.getSalesChannel().getId(),
                order.getExternalOrderId(),
                order.getOrderDate(),
                order.getStatus(),
                items
        );
    }

    private OrderItemResponseDTO toItemResponseDTO(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        return new OrderItemResponseDTO(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                subtotal
        );
    }
}
