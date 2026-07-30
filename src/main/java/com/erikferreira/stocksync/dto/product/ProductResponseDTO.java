package com.erikferreira.stocksync.dto.product;


import java.math.BigDecimal;

public record ProductResponseDTO(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal basePrice,
        boolean active,
        Integer availableQuantity // It comes from the Inventory, "flattened" to make it easier to consume.
) {
}
