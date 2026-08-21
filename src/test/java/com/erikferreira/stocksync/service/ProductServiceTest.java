package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.inventory.InventoryResponseDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductResponseDTO;
import com.erikferreira.stocksync.dto.product.ProductUpdateDTO;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.repository.ProductRepository;
import com.erikferreira.stocksync.service.exceptions.DatabaseException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductService service;

    private Product product;
    private Product productWithInventory;
    private Pageable pageable;
    private Long existingId;

    @BeforeEach
    void setUp() {
        product = ProductFactory.createProduct();
        productWithInventory = ProductFactory.createProductWithInventory();
        pageable = PageRequest.of(0, 10);
        existingId = 1L;
    }

    @Test
    void findAllPagedShouldReturnPageOfProducts() {
        Page<Product> page = new PageImpl<>(List.of(productWithInventory), pageable, 1);
        when(repository.findAll(pageable)).thenReturn(page);

        Page<ProductResponseDTO> result = service.findAllPaged(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getContent().get(0).id()).isEqualTo(existingId);
        assertThat(result.getContent().get(0).availableQuantity()).isEqualTo(50);
        verify(repository).findAll(pageable);
    }

    @Test
    void findAllPagedShouldReturnEmptyPageWhenNoProductsExist() {
        Page<Product> page = new PageImpl<>(List.of(), pageable, 0);
        when(repository.findAll(pageable)).thenReturn(page);

        Page<ProductResponseDTO> result = service.findAllPaged(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(repository).findAll(pageable);
    }

    @Test
    void findAllPagedShouldReturnZeroQuantityWhenProductHasNoInventory() {
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
        when(repository.findAll(pageable)).thenReturn(page);

        Page<ProductResponseDTO> result = service.findAllPaged(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getContent().get(0).id()).isEqualTo(existingId);
        assertThat(result.getContent().get(0).availableQuantity()).isZero();
        verify(repository).findAll(pageable);
    }

    @Test
    void findByIdShouldReturnProductWhenProductExists() {
        when(repository.findById(existingId)).thenReturn(Optional.of(productWithInventory));

        ProductResponseDTO result = service.findById(existingId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(existingId);
        assertThat(result.availableQuantity()).isEqualTo(50);
        verify(repository).findById(existingId);
    }

    @Test
    void findByIdShouldReturnZeroQuantityWhenProductHasNoInventory() {
        when(repository.findById(existingId)).thenReturn(Optional.of(product));

        ProductResponseDTO result = service.findById(existingId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(existingId);
        assertThat(result.availableQuantity()).isZero();
        verify(repository).findById(existingId);
    }

    @Test
    void findByIdShouldThrowExceptionWhenProductDoesNotExist() {
        when(repository.findById(existingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(existingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id " + existingId);

        verify(repository).findById(existingId);
    }

    @Test
    void getProductEntityByIdShouldReturnProductWhenProductExists() {
        when(repository.findById(existingId)).thenReturn(Optional.of(product));

        Product result = service.getProductEntityById(existingId);

        assertThat(result).isSameAs(product);
        verify(repository).findById(existingId);
    }

    @Test
    void getProductEntityByIdShouldThrowExceptionWhenProductDoesNotExist() {
        when(repository.findById(existingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductEntityById(existingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found with id " + existingId);

        verify(repository).findById(existingId);
    }

    @Test
    void insertShouldSaveProductAndCreateInitialInventory() {
        ProductInsertDTO dto = new ProductInsertDTO(
                "SKU-NEW",
                "New product",
                "New product description",
                new BigDecimal("29.90"),
                20,
                5
        );
        InventoryResponseDTO inventoryResponse = new InventoryResponseDTO(
                1L,
                existingId,
                20,
                5,
                LocalDateTime.now()
        );
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product savedProduct = invocation.getArgument(0);
            savedProduct.setId(existingId);
            return savedProduct;
        });
        when(inventoryService.createInitialInventory(any(Product.class), eq(20), eq(5)))
                .thenReturn(inventoryResponse);

        ProductResponseDTO result = service.insert(dto);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();

        assertThat(savedProduct.getSku()).isEqualTo(dto.sku());
        assertThat(savedProduct.getName()).isEqualTo(dto.name());
        assertThat(savedProduct.getDescription()).isEqualTo(dto.description());
        assertThat(savedProduct.getBasePrice()).isEqualByComparingTo(dto.basePrice());
        assertThat(savedProduct.isActive()).isTrue();
        assertThat(result.id()).isEqualTo(existingId);
        assertThat(result.availableQuantity()).isEqualTo(dto.initialQuantity());
        verify(inventoryService).createInitialInventory(
                savedProduct,
                dto.initialQuantity(),
                dto.minQuantity()
        );
    }

    @Test
    void updateShouldUpdateProductWhenProductExists() {
        ProductUpdateDTO dto = updatedProductDTO();
        when(repository.findById(existingId)).thenReturn(Optional.of(product));
        when(inventoryService.findByProduct(existingId)).thenReturn(inventoryResponseFor(product, 50));
        when(repository.save(product)).thenReturn(product);

        ProductResponseDTO result = service.update(existingId, dto);

        assertThat(result.id()).isEqualTo(existingId);
        assertThat(result.name()).isEqualTo(dto.name());
        assertThat(result.description()).isEqualTo(dto.description());
        assertThat(result.basePrice()).isEqualByComparingTo(dto.basePrice());
        assertThat(result.availableQuantity()).isEqualTo(50);
        verify(repository).findById(existingId);
        verify(inventoryService).findByProduct(existingId);
        verify(repository).save(product);
    }

    @Test
    void updateShouldKeepSkuAndActiveStatusUnchanged() {
        String originalSku = product.getSku();
        product.setActive(false);
        ProductUpdateDTO dto = updatedProductDTO();
        when(repository.findById(existingId)).thenReturn(Optional.of(product));
        when(inventoryService.findByProduct(existingId)).thenReturn(inventoryResponseFor(product, 50));
        when(repository.save(product)).thenReturn(product);

        ProductResponseDTO result = service.update(existingId, dto);

        assertThat(result.sku()).isEqualTo(originalSku);
        assertThat(result.active()).isFalse();
    }

    @Test
    void updateShouldThrowExceptionWhenProductDoesNotExist() {
        ProductUpdateDTO dto = updatedProductDTO();
        when(repository.findById(existingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(existingId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found: " + existingId);

        verify(repository).findById(existingId);
        verify(inventoryService, never()).findByProduct(any());
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void hardDeleteShouldDeleteProductWhenProductExists() {
        when(repository.findById(existingId)).thenReturn(Optional.of(product));

        service.hardDelete(existingId);

        verify(repository).findById(existingId);
        verify(repository).delete(product);
        verify(repository).flush();
    }

    @Test
    void hardDeleteShouldThrowExceptionWhenProductDoesNotExist() {
        when(repository.findById(existingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hardDelete(existingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found: " + existingId);

        verify(repository).findById(existingId);
        verify(repository, never()).delete(any(Product.class));
        verify(repository, never()).flush();
    }

    @Test
    void hardDeleteShouldThrowDatabaseExceptionWhenProductHasRelatedRecords() {
        when(repository.findById(existingId)).thenReturn(Optional.of(product));
        doThrow(new DataIntegrityViolationException("Product has related records"))
                .when(repository).flush();

        assertThatThrownBy(() -> service.hardDelete(existingId))
                .isInstanceOf(DatabaseException.class)
                .hasMessage("Cannot delete product with id " + existingId + ": it has related records");

        verify(repository).delete(product);
        verify(repository).flush();
    }

    @Test
    void activateShouldActivateProductWhenProductExists() {
        product.setActive(false);
        when(repository.findById(existingId)).thenReturn(Optional.of(product));
        when(repository.save(product)).thenReturn(product);

        service.activate(existingId);

        assertThat(product.isActive()).isTrue();
        verify(repository).findById(existingId);
        verify(repository).save(product);
    }

    @Test
    void activateShouldThrowExceptionWhenProductDoesNotExist() {
        when(repository.findById(existingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(existingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found: " + existingId);

        verify(repository).findById(existingId);
        verify(repository, never()).save(any(Product.class));
    }

    @Test
    void deactivateShouldDeactivateProductWhenProductExists() {
        product.setActive(true);
        when(repository.findById(existingId)).thenReturn(Optional.of(product));
        when(repository.save(product)).thenReturn(product);

        service.deactivate(existingId);

        assertThat(product.isActive()).isFalse();
        verify(repository).findById(existingId);
        verify(repository).save(product);
    }

    @Test
    void deactivateShouldThrowExceptionWhenProductDoesNotExist() {
        when(repository.findById(existingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(existingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found: " + existingId);

        verify(repository).findById(existingId);
        verify(repository, never()).save(any(Product.class));
    }

    private ProductUpdateDTO updatedProductDTO() {
        return new ProductUpdateDTO(
                "Updated product",
                "Updated description",
                new BigDecimal("39.90")
        );
    }

    private InventoryResponseDTO inventoryResponseFor(Product inventoryProduct, Integer availableQuantity) {
        return new InventoryResponseDTO(
                1L,
                inventoryProduct.getId(),
                availableQuantity,
                1,
                LocalDateTime.now()
        );
    }
}
