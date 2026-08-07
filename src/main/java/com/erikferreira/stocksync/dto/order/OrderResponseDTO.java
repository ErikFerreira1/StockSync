package com.erikferreira.stocksync.dto.order;

import com.erikferreira.stocksync.dto.orderItem.OrderItemResponseDTO;
import com.erikferreira.stocksync.entity.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseDTO(
        Long id,
        Long salesChannelId,
        String externalOrderId,
        LocalDateTime orderDate,
        OrderStatus status,
        List<OrderItemResponseDTO> items
) {
}
