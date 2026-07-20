package com.erikferreira.stocksync.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductInsertDTO(
        @NotBlank @Size(max = 100) String sku,

        @NotBlank String name,

        String description,

        @NotNull @DecimalMin("0.0") BigDecimal basePrice,

        @NotNull @PositiveOrZero Integer initialQuantity, // cria o Inventory inicial

        @NotNull @PositiveOrZero Integer minQuantity) {
}
