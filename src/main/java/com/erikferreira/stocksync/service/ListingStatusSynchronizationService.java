package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingStatusSynchronizationService {

    private final MarketplaceListingRepository listingRepository;
    private final InventoryService inventoryService;
    private final ListingStatusSynchronizationProcessor processor;
    private final SyncEventService syncEventService;

    public void synchronizeProductListings(Long productId, boolean productActive) {
        ListingStatus targetStatus = productActive ? ListingStatus.ACTIVE : ListingStatus.PAUSED;
        List<MarketplaceListing> listings = findListingsToSynchronize(productId, productActive);
        Integer availableQuantity = productActive && !listings.isEmpty()
                ? inventoryService.getAvailableQuantity(productId)
                : null;

        for (MarketplaceListing listing : listings) {
            try {
                processor.synchronizeListing(listing, targetStatus, availableQuantity);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to update status for listing {} after product {} was {}",
                        listing.getListingId(),
                        productId,
                        productActive ? "activated" : "deactivated",
                        exception);
                registerFailureEvent(listing, exception);
            }
        }
    }

    private List<MarketplaceListing> findListingsToSynchronize(Long productId, boolean productActive) {
        if (productActive) {
            return listingRepository
                    .findByProductIdAndStatusAndPausedByProductDeactivationTrueAndSalesChannelActiveTrue(
                            productId,
                            ListingStatus.PAUSED);
        }

        return listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(
                productId,
                ListingStatus.ACTIVE);
    }

    private void registerFailureEvent(MarketplaceListing listing, RuntimeException exception) {
        try {
            syncEventService.registerListingStatusFailure(
                    listing.getProduct().getId(),
                    listing.getSalesChannel().getId(),
                    exception);
        } catch (RuntimeException eventException) {
            log.error(
                    "Failed to register listing-status synchronization failure for listing {}",
                    listing.getListingId(),
                    eventException);
        }
    }
}
