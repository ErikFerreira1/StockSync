package com.erikferreira.stocksync.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductResponseDTO(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal basePrice,
        boolean active,
        Integer availableQuantity // vem do Inventory, "achatado" pra facilitar o consumo
) {
}
