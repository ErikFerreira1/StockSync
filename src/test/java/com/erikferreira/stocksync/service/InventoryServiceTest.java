package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.inventory.AdjustStockDTO;
import com.erikferreira.stocksync.dto.inventory.InventoryResponseDTO;
import com.erikferreira.stocksync.dto.inventory.StockRequestDTO;
import com.erikferreira.stocksync.dto.inventory.UpdateMinQuantityDTO;
import com.erikferreira.stocksync.entity.Inventory;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.event.StockChangedEvent;
import com.erikferreira.stocksync.factory.InventoryFactory;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.repository.InventoryRepository;
import com.erikferreira.stocksync.service.exceptions.InsufficientStockException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository repository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private InventoryService service;

    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        product = ProductFactory.createProduct();
        inventory = InventoryFactory.createInventory(product);
        inventory.setId(1L);
    }

    @Test
    void findByProductShouldReturnInventory() {
        when(repository.findByProductId(1L)).thenReturn(Optional.of(inventory));

        InventoryResponseDTO result = service.findByProduct(1L);

        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.availableQuantity()).isEqualTo(50);
    }

    @Test
    void findByProductShouldThrowWhenInventoryDoesNotExist() {
        when(repository.findByProductId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByProduct(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findProductsBelowMinimumShouldMapResults() {
        inventory.setAvailableQuantity(1);
        when(repository.findAllBelowMinimum()).thenReturn(List.of(inventory));

        List<InventoryResponseDTO> result = service.findProductsBelowMinimum();

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.productId()).isEqualTo(1L);
            assertThat(dto.availableQuantity()).isEqualTo(1);
        });
    }

    @Test
    void createInitialInventoryShouldSaveMappedInventory() {
        when(repository.save(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        InventoryResponseDTO result = service.createInitialInventory(product, 10, 2);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getProduct()).isSameAs(product);
        assertThat(captor.getValue().getAvailableQuantity()).isEqualTo(10);
        assertThat(captor.getValue().getMinQuantity()).isEqualTo(2);
        assertThat(result.id()).isEqualTo(2L);
    }

    @Test
    void increaseStockShouldSaveNewQuantityAndPublishEvent() {
        when(repository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        service.increaseStock(new StockRequestDTO(1L, 5));

        assertThat(inventory.getAvailableQuantity()).isEqualTo(55);
        verify(repository).save(inventory);
        verifyStockChangedEvent(1L);
    }

    @Test
    void decreaseStockShouldSaveNewQuantityAndPublishEvent() {
        when(repository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        service.decreaseStock(new StockRequestDTO(1L, 5));

        assertThat(inventory.getAvailableQuantity()).isEqualTo(45);
        verify(repository).save(inventory);
        verifyStockChangedEvent(1L);
    }

    @Test
    void decreaseStockShouldThrowWhenStockIsInsufficient() {
        when(repository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> service.decreaseStock(new StockRequestDTO(1L, 51)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Available: 50");

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void decreaseStockShouldThrowWhenInventoryDoesNotExist() {
        when(repository.findByProductIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decreaseStock(new StockRequestDTO(1L, 5)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inventory not found for product: 1");

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void adjustStockShouldDoNothingWhenQuantityIsUnchanged() {
        when(repository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        service.adjustStock(new AdjustStockDTO(1L, 50));

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void adjustStockShouldSaveAndPublishWhenQuantityChanges() {
        when(repository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        service.adjustStock(new AdjustStockDTO(1L, 25));

        assertThat(inventory.getAvailableQuantity()).isEqualTo(25);
        verify(repository).save(inventory);
        verifyStockChangedEvent(1L);
    }

    @Test
    void updateMinQuantityShouldSaveAndReturnInventory() {
        when(repository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        InventoryResponseDTO result = service.updateMinQuantity(1L, new UpdateMinQuantityDTO(7));

        assertThat(inventory.getMinQuantity()).isEqualTo(7);
        assertThat(result.minQuantity()).isEqualTo(7);
        verify(repository).save(inventory);
    }



    private void verifyStockChangedEvent(Long productId) {
        ArgumentCaptor<StockChangedEvent> captor = ArgumentCaptor.forClass(StockChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().productId()).isEqualTo(productId);
    }
}
