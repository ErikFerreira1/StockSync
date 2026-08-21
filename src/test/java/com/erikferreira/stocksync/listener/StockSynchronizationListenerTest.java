package com.erikferreira.stocksync.listener;

import com.erikferreira.stocksync.event.StockChangedEvent;
import com.erikferreira.stocksync.service.StockSynchronizationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StockSynchronizationListenerTest {

    @Test
    void handleStockChangedShouldSynchronizeEventProduct() {
        StockSynchronizationService service = mock(StockSynchronizationService.class);
        StockSynchronizationListener listener = new StockSynchronizationListener(service);

        listener.handleStockChanged(new StockChangedEvent(10L));

        verify(service).synchronizeProductStock(10L);
    }
}
