package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockSynchronizationProcessorTest {

    @Mock private MarketplaceIntegrationPort marketplaceIntegrationPort;
    @Mock private MarketplaceListingRepository listingRepository;
    @Mock private SyncEventService syncEventService;
    @InjectMocks private StockSynchronizationProcessor processor;

    private MarketplaceListing listing;

    @BeforeEach
    void setUp() {
        Product product = ProductFactory.createProduct();
        SalesChannel channel = SalesChannel.builder().id(2L).build();
        listing = MarketplaceListing.builder().id(3L).product(product).salesChannel(channel)
                .listingId("MLB123").build();
    }

    @Test
    void synchronizeListingShouldUpdateMarketplaceSaveTimestampAndRegisterSuccess() {
        processor.synchronizeListing(listing, 30);

        verify(marketplaceIntegrationPort).updateStock("MLB123", 30);
        assertThat(listing.getLastSyncedAt()).isNotNull();
        verify(listingRepository).save(listing);
        verify(syncEventService).registerStockSuccess(1L, 2L);
    }

    @Test
    void synchronizeListingShouldStopWhenMarketplaceUpdateFails() {
        doThrow(new RuntimeException("API failed"))
                .when(marketplaceIntegrationPort).updateStock("MLB123", 30);

        assertThatThrownBy(() -> processor.synchronizeListing(listing, 30))
                .isInstanceOf(RuntimeException.class);

        assertThat(listing.getLastSyncedAt()).isNull();
        verify(listingRepository, never()).save(listing);
        verify(syncEventService, never()).registerStockSuccess(1L, 2L);
    }
}
