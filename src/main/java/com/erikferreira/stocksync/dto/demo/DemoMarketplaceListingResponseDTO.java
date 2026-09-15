package com.erikferreira.stocksync.dto.demo;

import com.erikferreira.stocksync.entity.enums.ListingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record DemoMarketplaceListingResponseDTO(
        String listingId,
        Long productId,
        String title,
        BigDecimal price,
        Integer availableQuantity,
        ListingStatus status,
        Instant updatedAt
) {
}
