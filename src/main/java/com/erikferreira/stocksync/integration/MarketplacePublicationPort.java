package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.dto.marketplaceListing.*;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import java.util.List;

public interface MarketplacePublicationPort {
    List<PublicationCategoryDTO> searchCategories(Long salesChannelId, String query);
    PublicationOptionsDTO getOptions(Long salesChannelId, String categoryId);
    List<String> validate(PublicationRequestDTO request, int quantity);
    PublishedListing publish(PublicationRequestDTO request, int quantity);
    void addDescription(Long salesChannelId, String listingId, String description);
    record PublishedListing(String id, String url, ListingStatus status) {}
}
