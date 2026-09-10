package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
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
class ListingStatusSynchronizationProcessorTest {

    @Mock private MarketplaceIntegrationPort marketplaceIntegrationPort;
    @Mock private MarketplaceListingRepository listingRepository;
    @Mock private SyncEventService syncEventService;
    @InjectMocks private ListingStatusSynchronizationProcessor processor;

    private MarketplaceListing listing;

    @BeforeEach
    void setUp() {
        Product product = ProductFactory.createProduct();
        SalesChannel channel = SalesChannel.builder().id(2L).build();
        listing = MarketplaceListing.builder()
                .id(3L)
                .product(product)
                .salesChannel(channel)
                .listingId("MLB123")
                .status(ListingStatus.ACTIVE)
                .build();
    }

    @Test
    void synchronizeListingShouldPauseAndTrackProductDeactivation() {
        processor.synchronizeListing(listing, ListingStatus.PAUSED, null);

        verify(marketplaceIntegrationPort).updateListingStatus("MLB123", ListingStatus.PAUSED, null);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PAUSED);
        assertThat(listing.isPausedByProductDeactivation()).isTrue();
        assertThat(listing.getLastSyncedAt()).isNotNull();
        verify(listingRepository).save(listing);
        verify(syncEventService).registerListingStatusSuccess(1L, 2L);
    }

    @Test
    void synchronizeListingShouldActivateClearTrackingAndSendStock() {
        listing.setStatus(ListingStatus.PAUSED);
        listing.setPausedByProductDeactivation(true);

        processor.synchronizeListing(listing, ListingStatus.ACTIVE, 9);

        verify(marketplaceIntegrationPort).updateListingStatus("MLB123", ListingStatus.ACTIVE, 9);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.isPausedByProductDeactivation()).isFalse();
        verify(listingRepository).save(listing);
    }

    @Test
    void synchronizeListingShouldNotChangeLocalStateWhenMarketplaceFails() {
        doThrow(new RuntimeException("API failed"))
                .when(marketplaceIntegrationPort)
                .updateListingStatus("MLB123", ListingStatus.PAUSED, null);

        assertThatThrownBy(() -> processor.synchronizeListing(listing, ListingStatus.PAUSED, null))
                .isInstanceOf(RuntimeException.class);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(listing.isPausedByProductDeactivation()).isFalse();
        assertThat(listing.getLastSyncedAt()).isNull();
        verify(listingRepository, never()).save(listing);
        verify(syncEventService, never()).registerListingStatusSuccess(1L, 2L);
    }
}
