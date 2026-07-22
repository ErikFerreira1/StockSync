package com.erikferreira.stocksync.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductInsertDTO(
        @NotBlank(message = "SKU is required")
        @Size(max = 100, message = "SKU must have at most 100 characters")
        String sku,

        @NotBlank(message = "Product name is required")
        @Size(min = 3, max = 200, message = "Name must be between 3 and 200 characters")
        String name,

        @Size(max = 1000, message = "Description must have at most 1000 characters")
        String description,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.01", message = "Base price must be greater than zero")
        BigDecimal basePrice,

        @NotNull(message = "Initial quantity is required")
        @PositiveOrZero(message = "Initial quantity cannot be negative")
        Integer initialQuantity,

        @NotNull(message = "Minimum quantity is required")
        @PositiveOrZero(message = "Minimum quantity cannot be negative")
        Integer minQuantity) {
}
