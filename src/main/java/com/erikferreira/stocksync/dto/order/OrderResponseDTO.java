package com.erikferreira.stocksync.dto.order;

import com.erikferreira.stocksync.dto.orderItem.OrderItemResponseDTO;
import com.erikferreira.stocksync.entity.enums.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponseDTO(
        Long id,
        Long salesChannelId,
        String externalOrderId,
        Instant orderDate,
        OrderStatus status,
        List<OrderItemResponseDTO> items
) {
}
