package com.erikferreira.stocksync.listener;

import com.erikferreira.stocksync.event.ProductStatusChangedEvent;
import com.erikferreira.stocksync.service.ListingStatusSynchronizationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ListingStatusSynchronizationListenerTest {

    @Test
    void handleProductStatusChangedShouldSynchronizeEventProduct() {
        ListingStatusSynchronizationService service = mock(ListingStatusSynchronizationService.class);
        ListingStatusSynchronizationListener listener = new ListingStatusSynchronizationListener(service);

        listener.handleProductStatusChanged(new ProductStatusChangedEvent(10L, false));

        verify(service).synchronizeProductListings(10L, false);
    }
}
