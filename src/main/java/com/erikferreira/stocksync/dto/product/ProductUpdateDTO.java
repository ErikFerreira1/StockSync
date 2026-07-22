package com.erikferreira.stocksync.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductUpdateDTO(
        @NotBlank(message = "Name required ")
        String name,

        String description,

        @NotNull(message = "Field cannot be null")
        @DecimalMin("0.0")
        BigDecimal basePrice,

        boolean active) {
}
