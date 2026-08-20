package com.erikferreira.stocksync.listener;

import com.erikferreira.stocksync.event.StockChangedEvent;
import com.erikferreira.stocksync.service.StockSynchronizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class StockSynchronizationListener {

    private final StockSynchronizationService synchronizationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStockChanged(StockChangedEvent event) {
        synchronizationService.synchronizeProductStock(
                event.productId()
        );
    }
}
