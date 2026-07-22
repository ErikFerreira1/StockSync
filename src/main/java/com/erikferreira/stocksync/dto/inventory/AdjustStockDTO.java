package com.erikferreira.stocksync.dto.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AdjustStockDTO(
        @NotNull(message = "Product ID cannot be null")
        @Positive(message = "Product ID must be positive")
        Long productId,

        @NotNull(message = "New quantity cannot be null")
        @PositiveOrZero(message = "New quantity cannot be negative")
        Integer quantity
) {}
