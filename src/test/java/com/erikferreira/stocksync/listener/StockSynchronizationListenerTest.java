package com.erikferreira.stocksync.listener;

import com.erikferreira.stocksync.event.StockChangedEvent;
import com.erikferreira.stocksync.event.MarketplaceListingCreatedEvent;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.adapter.DemoMarketplaceAdapter;
import com.erikferreira.stocksync.service.StockSynchronizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StockSynchronizationListenerTest {

    @Test
    void handleStockChangedShouldSynchronizeEventProduct() {
        StockSynchronizationService service = mock(StockSynchronizationService.class);
        StockSynchronizationListener listener = new StockSynchronizationListener(service,
                new StaticListableBeanFactory().getBeanProvider(DemoMarketplaceAdapter.class));

        listener.handleStockChanged(new StockChangedEvent(10L));

        verify(service).synchronizeProductStock(10L);
    }

    @Test
    void handleActiveListingCreatedShouldRegisterDemoListingAndSynchronizeStock() {
        StockSynchronizationService service = mock(StockSynchronizationService.class);
        DemoMarketplaceAdapter adapter = mock(DemoMarketplaceAdapter.class);
        var beanFactory = new StaticListableBeanFactory(Map.of("demoMarketplaceAdapter", adapter));
        StockSynchronizationListener listener = new StockSynchronizationListener(service,
                beanFactory.getBeanProvider(DemoMarketplaceAdapter.class));

        listener.handleListingCreated(new MarketplaceListingCreatedEvent(
                10L, "MLB-10", "Produto teste", new BigDecimal("19.90"), 7, ListingStatus.ACTIVE));

        verify(adapter).registerListing("MLB-10", 10L, "Produto teste", new BigDecimal("19.90"), 7, ListingStatus.ACTIVE);
        verify(service).synchronizeProductStock(10L);
    }

    @Test
    void handleInactiveListingCreatedShouldNotSynchronizeStock() {
        StockSynchronizationService service = mock(StockSynchronizationService.class);
        StockSynchronizationListener listener = new StockSynchronizationListener(service,
                new StaticListableBeanFactory().getBeanProvider(DemoMarketplaceAdapter.class));

        listener.handleListingCreated(new MarketplaceListingCreatedEvent(
                10L, "MLB-10", "Produto teste", new BigDecimal("19.90"), 7, ListingStatus.PAUSED));

        verify(service, org.mockito.Mockito.never()).synchronizeProductStock(10L);
    }
}
