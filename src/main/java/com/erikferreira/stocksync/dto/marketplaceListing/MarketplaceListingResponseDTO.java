package com.erikferreira.stocksync.dto.marketplaceListing;

import com.erikferreira.stocksync.entity.enums.ListingStatus;

import java.time.Instant;

public record MarketplaceListingResponseDTO(
        Long id,
        Long productId,
        Long salesChannelId,
        String listingId,
        String listingUrl,
        ListingStatus status,
        Instant lastSyncedAt
) {
}
