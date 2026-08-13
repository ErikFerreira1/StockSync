package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;

import java.util.List;

public interface MarketplaceIntegrationPort {
    List<ExternalOrderDTO> fetchNewOrders(Long salesChannelId);
    void updateStock(String listingId, Integer quantity);
    Integer getCurrentStock(String listingId);
}
