package com.erikferreira.stocksync.listener;

import com.erikferreira.stocksync.event.ProductStatusChangedEvent;
import com.erikferreira.stocksync.service.ListingStatusSynchronizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ListingStatusSynchronizationListener {

    private final ListingStatusSynchronizationService synchronizationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductStatusChanged(ProductStatusChangedEvent event) {
        synchronizationService.synchronizeProductListings(event.productId(), event.active());
    }
}
