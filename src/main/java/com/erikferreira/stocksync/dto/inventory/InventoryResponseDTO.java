package com.erikferreira.stocksync.dto.inventory;

import java.time.Instant;

public record InventoryResponseDTO(
        Long id,
        Long productId,
        Integer availableQuantity,
        Integer minQuantity,
        Instant updatedAt
) {}
