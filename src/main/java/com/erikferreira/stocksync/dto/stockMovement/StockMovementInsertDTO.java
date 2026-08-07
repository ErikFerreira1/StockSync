package com.erikferreira.stocksync.dto.stockMovement;

import com.erikferreira.stocksync.entity.enums.MovementType;
import com.erikferreira.stocksync.entity.enums.OriginType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockMovementInsertDTO(
        @NotNull(message = "Product ID cannot be null")
        @Positive(message = "Product ID must be positive")
        Long productId,

        @NotNull(message = "Quantity cannot be null")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "Movement type cannot be null")
        MovementType type,

        OriginType originType,

        @Positive(message = "Origin ID must be positive")
        Long originId,

        @Size(max = 255, message = "Note must be at most 255 characters")
        String note
) {}