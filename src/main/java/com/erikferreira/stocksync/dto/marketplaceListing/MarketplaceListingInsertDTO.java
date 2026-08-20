package com.erikferreira.stocksync.dto.marketplaceListing;

import com.erikferreira.stocksync.entity.enums.ListingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record MarketplaceListingInsertDTO(
        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        Long productId,

        @NotNull(message = "Sales channel ID is required")
        @Positive(message = "Sales channel ID must be positive")
        Long salesChannelId,

        @NotBlank(message = "Listing ID is required")
        @Size(max = 255, message = "Listing ID must have at most 255 characters")
        String listingId,

        @URL(message = "Listing URL must be a valid URL")
        @Size(max = 1000, message = "Listing URL must have at most 1000 characters")
        String listingUrl,

        @NotNull(message = "Listing status is required")
        ListingStatus status
) {
}
