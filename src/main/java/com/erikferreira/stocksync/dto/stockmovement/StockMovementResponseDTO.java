package com.erikferreira.stocksync.dto.stockmovement;

import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OriginType;

import java.time.LocalDateTime;

public record StockMovementResponseDTO(
        Long id,
        Long productId,
        LocalDateTime occurredAt,
        Integer quantity,
        MovementType type,
        OriginType originType,
        Long originId,
        String note
) {
}
