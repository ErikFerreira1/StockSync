package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;

import java.util.List;

public interface MarketplaceIntegrationPort {
    List<ExternalOrderDTO> fetchNewOrders(Long salesChannelId);
    void updateStock(String listingId, Integer quantity);
    void updateListingStatus(String listingId, ListingStatus status, Integer availableQuantity);
    Integer getCurrentStock(String listingId);
}
