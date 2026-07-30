package com.erikferreira.stocksync.dto.order;

import com.erikferreira.stocksync.dto.orderitem.OrderItemInsertDTO;
import com.erikferreira.stocksync.entity.enums.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.time.LocalDateTime;
import java.util.List;

public record OrderInsertDTO(
        @NotBlank(message = "External order ID is required")
        String externalOrderId,

        @NotNull(message = "Sales channel ID is required")
        Long salesChannelId,

        @NotNull(message = "Order date is required")
        LocalDateTime orderDate,

        @Valid
        @NotNull(message = "Items are required")
        @Size(min = 1, message = "Order must have at least one item")
        List<OrderItemInsertDTO> items
) {
}
