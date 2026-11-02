package com.erikferreira.stocksync.event;

import com.erikferreira.stocksync.entity.enums.ListingStatus;

import java.math.BigDecimal;

public record MarketplaceListingCreatedEvent(
        Long productId,
        String listingId,
        String title,
        BigDecimal price,
        Integer availableQuantity,
        ListingStatus status
) {}
