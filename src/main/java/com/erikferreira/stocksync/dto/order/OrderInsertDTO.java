package com.erikferreira.stocksync.dto.order;

import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.time.Instant;
import java.util.List;

public record OrderInsertDTO(
        @NotBlank(message = "External order ID is required")
        String externalOrderId,

        @NotNull(message = "Sales channel ID is required")
        Long salesChannelId,

        @NotNull(message = "Order date is required")
        Instant orderDate,

        @Valid
        @NotNull(message = "Items are required")
        @Size(min = 1, message = "Order must have at least one item")
        List<OrderItemInsertDTO> items
) {
}
