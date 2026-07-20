package com.erikferreira.stocksync.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductUpdateDTO(
        @NotBlank String name,

        String description,

        @NotNull @DecimalMin("0.0") BigDecimal basePrice,

        boolean active) {
}
