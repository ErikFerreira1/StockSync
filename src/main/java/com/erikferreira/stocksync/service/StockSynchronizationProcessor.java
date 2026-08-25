package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StockSynchronizationProcessor {

    private final MarketplaceIntegrationPort marketplaceIntegrationPort;
    private final MarketplaceListingRepository listingRepository;
    private final SyncEventService syncEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchronizeListing(MarketplaceListing listing, Integer quantity) {
        marketplaceIntegrationPort.updateStock(listing.getListingId(), quantity);
        listing.setLastSyncedAt(Instant.now());

        listingRepository.save(listing);

        syncEventService.registerStockSuccess(
                listing.getProduct().getId(),
                listing.getSalesChannel().getId()
        );

    }
}
