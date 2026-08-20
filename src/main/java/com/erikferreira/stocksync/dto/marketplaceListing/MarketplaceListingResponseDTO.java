package com.erikferreira.stocksync.dto.marketplaceListing;

import com.erikferreira.stocksync.entity.enums.ListingStatus;

import java.time.LocalDateTime;

public record MarketplaceListingResponseDTO(
        Long id,
        Long productId,
        Long salesChannelId,
        String listingId,
        String listingUrl,
        ListingStatus status,
        LocalDateTime lastSyncedAt
) {
}
