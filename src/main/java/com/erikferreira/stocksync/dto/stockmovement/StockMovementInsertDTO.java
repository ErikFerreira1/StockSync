package com.erikferreira.stocksync.dto.stockmovement;

import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OriginType;

public record StockMovementInsertDTO(
        Long productId,
        Integer quantity,
        MovementType type,
        OriginType originType,
        Long originId,
        String note
) {
}
