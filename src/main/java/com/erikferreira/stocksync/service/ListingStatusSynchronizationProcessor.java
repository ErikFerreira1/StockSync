package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ListingStatusSynchronizationProcessor {

    private final MarketplaceIntegrationPort marketplaceIntegrationPort;
    private final MarketplaceListingRepository listingRepository;
    private final SyncEventService syncEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchronizeListing(
            MarketplaceListing listing,
            ListingStatus targetStatus,
            Integer availableQuantity) {
        marketplaceIntegrationPort.updateListingStatus(
                listing.getListingId(),
                targetStatus,
                availableQuantity);

        listing.setStatus(targetStatus);
        listing.setPausedByProductDeactivation(targetStatus == ListingStatus.PAUSED);
        listing.setLastSyncedAt(Instant.now());
        listingRepository.save(listing);

        syncEventService.registerListingStatusSuccess(
                listing.getProduct().getId(),
                listing.getSalesChannel().getId());
    }
}
