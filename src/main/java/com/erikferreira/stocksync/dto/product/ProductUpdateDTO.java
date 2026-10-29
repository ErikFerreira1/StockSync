package com.erikferreira.stocksync.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateDTO(
        @Size(max = 100, message = "SKU must have at most 100 characters")
        String sku,

        @NotBlank(message = "Name required ")
        String name,

        String description,

        @NotNull(message = "Field cannot be null")
        @DecimalMin("0.0")
        BigDecimal basePrice
        ) {
    public ProductUpdateDTO(String name, String description, BigDecimal basePrice) {
        this(null, name, description, basePrice);
    }
}
