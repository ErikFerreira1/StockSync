package com.erikferreira.stocksync.dto.stockMovement;

import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OriginType;

import java.time.Instant;

public record StockMovementResponseDTO(
        Long id,
        Long productId,
        Instant occurredAt,
        Integer quantity,
        MovementType type,
        OriginType originType,
        Long originId,
        String note
) {
}
