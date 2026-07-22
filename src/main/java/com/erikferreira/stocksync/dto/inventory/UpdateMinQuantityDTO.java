package com.erikferreira.stocksync.dto.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMinQuantityDTO(
        @NotNull(message = "Minimum quantity cannot be null")
        @PositiveOrZero(message = "Minimum quantity cannot be negative")
        Integer minQuantity
) {
}
