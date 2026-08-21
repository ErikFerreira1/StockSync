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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockSynchronizationServiceTest {

    @Mock private MarketplaceListingRepository listingRepository;
    @Mock private InventoryService inventoryService;
    @Mock private StockSynchronizationProcessor processor;
    @Mock private SyncEventService syncEventService;
    @InjectMocks private StockSynchronizationService service;

    private MarketplaceListing first;
    private MarketplaceListing second;

    @BeforeEach
    void setUp() {
        Product product = ProductFactory.createProduct();
        SalesChannel channel = SalesChannel.builder().id(2L).active(true).build();
        first = MarketplaceListing.builder().id(3L).product(product).salesChannel(channel)
                .listingId("MLB1").status(ListingStatus.ACTIVE).build();
        second = MarketplaceListing.builder().id(4L).product(product).salesChannel(channel)
                .listingId("MLB2").status(ListingStatus.ACTIVE).build();
    }

    @Test
    void synchronizeProductStockShouldProcessEveryActiveListing() {
        when(inventoryService.getAvailableQuantity(1L)).thenReturn(25);
        when(listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(1L, ListingStatus.ACTIVE))
                .thenReturn(List.of(first, second));

        service.synchronizeProductStock(1L);

        verify(processor).synchronizeListing(first, 25);
        verify(processor).synchronizeListing(second, 25);
    }

    @Test
    void synchronizeProductStockShouldRegisterFailureAndContinue() {
        when(inventoryService.getAvailableQuantity(1L)).thenReturn(25);
        when(listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(1L, ListingStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        RuntimeException failure = new RuntimeException("marketplace failed");
        doThrow(failure).when(processor).synchronizeListing(first, 25);

        service.synchronizeProductStock(1L);

        verify(syncEventService).registerStockFailure(1L, 2L, failure);
        verify(processor).synchronizeListing(second, 25);
    }

    @Test
    void synchronizeProductStockShouldSwallowFailureEventError() {
        when(inventoryService.getAvailableQuantity(1L)).thenReturn(25);
        when(listingRepository.findByProductIdAndStatusAndSalesChannelActiveTrue(1L, ListingStatus.ACTIVE))
                .thenReturn(List.of(first));
        RuntimeException failure = new RuntimeException("marketplace failed");
        doThrow(failure).when(processor).synchronizeListing(first, 25);
        doThrow(new RuntimeException("event failed"))
                .when(syncEventService).registerStockFailure(1L, 2L, failure);

        service.synchronizeProductStock(1L);

        verify(syncEventService).registerStockFailure(1L, 2L, failure);
    }
}
