package com.erikferreira.stocksync.dto.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record StockRequestDTO(
        @NotNull(message = "Product ID cannot null")
        @Positive(message = "Product ID must be positive ")
        Long productId,

        @NotNull(message = "Quantity cannot be null")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity
) {
}
