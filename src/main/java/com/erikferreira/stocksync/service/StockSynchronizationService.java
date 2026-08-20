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
public class StockSynchronizationService {

    private final MarketplaceListingRepository listingRepository;
    private final InventoryService inventoryService;
    private final StockSynchronizationProcessor processor;
    private final SyncEventService syncEventService;

    public void synchronizeProductStock(Long productId) {
        Integer quantity = inventoryService.getAvailableQuantity(productId);

        List<MarketplaceListing> activeListings =
                listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(
                        productId,
                        ListingStatus.ACTIVE);

        for (MarketplaceListing listing : activeListings) {
            try {
                processor.synchronizeListing(listing, quantity);
            } catch (RuntimeException exception) {
                log.error("Failed to synchronize stock for listing {}", listing.getListingId(), exception);

                registerFailureEvent(listing, exception);
            }
    }
}
    private void registerFailureEvent(MarketplaceListing listing, RuntimeException exception) {

        try {
            syncEventService.registerStockFailure(
                    listing.getProduct().getId(),
                    listing.getSalesChannel().getId(),
                    exception
            );

        } catch (RuntimeException eventException) {
            log.error("Failed to register stock synchronization failure for listing {}", listing.getListingId(), eventException);
        }
    }
}
