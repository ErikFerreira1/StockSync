package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductResponseDTO;
import com.erikferreira.stocksync.dto.product.ProductUpdateDTO;
import com.erikferreira.stocksync.entity.Inventory;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.repository.InventoryRepository;
import com.erikferreira.stocksync.repository.ProductRepository;
import com.erikferreira.stocksync.service.exceptions.DatabaseException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAllPaged(Pageable pageable) {
        Page<Product> list = repository.findAll(pageable);

        return list.map(product -> toResponseDTO(product, product.getInventory()));
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findById(Long id) {
        Product entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entity not found with id " + id));

        return toResponseDTO(entity, entity.getInventory());
    }

    @Transactional
    public ProductResponseDTO insert(ProductInsertDTO dto) {
        Product entity = new Product();
        copyInsertDtoToEntity(dto, entity);
        repository.save(entity);

        Inventory inventory = Inventory.builder()
                .product(entity)
                .availableQuantity(dto.initialQuantity())
                .minQuantity(dto.minQuantity())
                .updatedAt(LocalDateTime.now())
                .build();
        
        inventoryRepository.save(inventory);
        
        return toResponseDTO(entity, inventory);
    }

    @Transactional
    public ProductResponseDTO update(Long id, ProductUpdateDTO dto) {
        Product entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        Inventory inventory = inventoryRepository.findByProductId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + id));

        copyUpdateDtoToEntity(dto, entity);
        entity = repository.save(entity);

        return toResponseDTO(entity, inventory);
    }

    @Transactional
    public void hardDelete(Long id) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        try {
            repository.delete(product);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseException(
                    "Cannot delete product with id " + id + ": it has related records"
            );
        }
    }

    @Transactional
    public void deactivate(Long id) {
        setActive(id, false);
    }

    @Transactional
    public void activate(Long id) {
        setActive(id, true);
    }

    private void copyInsertDtoToEntity(ProductInsertDTO dto, Product entity) {
        entity.setSku(dto.sku());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setBasePrice(dto.basePrice());
        entity.setActive(true);
    }

    private void copyUpdateDtoToEntity(ProductUpdateDTO dto, Product entity) {
        // sem sku
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setBasePrice(dto.basePrice());
        entity.setActive(dto.active());
    }

    private ProductResponseDTO toResponseDTO(Product product, Inventory inventory) {
        Integer quantity = inventory != null ? inventory.getAvailableQuantity() : null;
        return new ProductResponseDTO(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.isActive(),
                quantity
        );
    }

    private void setActive(Long id, boolean active) {
        Product entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        entity.setActive(active);
        repository.save(entity);
    }

}
