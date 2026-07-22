package com.erikferreira.stocksync.dto.inventory;

import java.time.LocalDateTime;

public record InventoryResponseDTO(
        Long id,
        Long productId,
        Integer availableQuantity,
        Integer minQuantity,
        LocalDateTime updatedAt
) {}