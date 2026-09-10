package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingStatusSynchronizationServiceTest {

    @Mock private MarketplaceListingRepository listingRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ListingStatusSynchronizationProcessor processor;
    @Mock private SyncEventService syncEventService;
    @InjectMocks private ListingStatusSynchronizationService service;

    private MarketplaceListing first;
    private MarketplaceListing second;

    @BeforeEach
    void setUp() {
        Product product = ProductFactory.createProduct();
        SalesChannel channel = SalesChannel.builder().id(2L).active(true).build();
        first = MarketplaceListing.builder()
                .id(3L)
                .product(product)
                .salesChannel(channel)
                .listingId("MLB1")
                .status(ListingStatus.ACTIVE)
                .build();
        second = MarketplaceListing.builder()
                .id(4L)
                .product(product)
                .salesChannel(channel)
                .listingId("MLB2")
                .status(ListingStatus.ACTIVE)
                .build();
    }

    @Test
    void deactivationShouldPauseEveryActiveListingWithoutSendingQuantity() {
        when(listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(
                1L,
                ListingStatus.ACTIVE)).thenReturn(List.of(first, second));

        service.synchronizeProductListings(1L, false);

        verify(processor).synchronizeListing(first, ListingStatus.PAUSED, null);
        verify(processor).synchronizeListing(second, ListingStatus.PAUSED, null);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void activationShouldReactivateOnlyListingsPausedByProductDeactivationWithCurrentStock() {
        first.setStatus(ListingStatus.PAUSED);
        first.setPausedByProductDeactivation(true);
        when(listingRepository
                .findByProductIdAndStatusAndPausedByProductDeactivationTrueAndSalesChannelActiveTrue(
                        1L,
                        ListingStatus.PAUSED)).thenReturn(List.of(first));
        when(inventoryService.getAvailableQuantity(1L)).thenReturn(9);

        service.synchronizeProductListings(1L, true);

        verify(processor).synchronizeListing(first, ListingStatus.ACTIVE, 9);
    }

    @Test
    void activationShouldNotReactivateUnmanagedPausedListings() {
        when(listingRepository
                .findByProductIdAndStatusAndPausedByProductDeactivationTrueAndSalesChannelActiveTrue(
                        1L,
                        ListingStatus.PAUSED)).thenReturn(List.of());

        service.synchronizeProductListings(1L, true);

        verifyNoInteractions(inventoryService, processor);
    }

    @Test
    void synchronizationShouldRegisterFailureAndContinue() {
        when(listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(
                1L,
                ListingStatus.ACTIVE)).thenReturn(List.of(first, second));
        RuntimeException failure = new RuntimeException("marketplace failed");
        doThrow(failure).when(processor).synchronizeListing(first, ListingStatus.PAUSED, null);

        service.synchronizeProductListings(1L, false);

        verify(syncEventService).registerListingStatusFailure(1L, 2L, failure);
        verify(processor).synchronizeListing(second, ListingStatus.PAUSED, null);
    }

    @Test
    void synchronizationShouldSwallowFailureEventError() {
        when(listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(
                1L,
                ListingStatus.ACTIVE)).thenReturn(List.of(first));
        RuntimeException failure = new RuntimeException("marketplace failed");
        doThrow(failure).when(processor).synchronizeListing(first, ListingStatus.PAUSED, null);
        doThrow(new RuntimeException("event failed"))
                .when(syncEventService).registerListingStatusFailure(1L, 2L, failure);

        service.synchronizeProductListings(1L, false);

        verify(syncEventService).registerListingStatusFailure(1L, 2L, failure);
        verify(inventoryService, never()).getAvailableQuantity(1L);
    }
}
