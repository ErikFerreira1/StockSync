package com.erikferreira.stocksync.dto.orderitem;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record OrderItemInsertDTO(
        @NotNull(message = "Product ID is required")
        Long productId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "Unit price is required")
        @PositiveOrZero(message = "Unit price must be greater than zero")
        BigDecimal unitPrice
) {
}
