package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.inventory.InventoryResponseDTO;
import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductResponseDTO;
import com.erikferreira.stocksync.dto.product.ProductUpdateDTO;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.event.ProductStatusChangedEvent;
import com.erikferreira.stocksync.repository.ProductRepository;
import com.erikferreira.stocksync.service.exceptions.DatabaseException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;


@Service
@RequiredArgsConstructor
@Validated
public class ProductService {

    private final ProductRepository repository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> findAllPaged(Pageable pageable) {
        Page<Product> list = repository.findAll(pageable);

        return list.map(product -> toResponseDTO(product,
                product.getInventory() != null ? product.getInventory().getAvailableQuantity() : 0));
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findById(Long id) {
        Product entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));

        return toResponseDTO(entity, entity.getInventory() != null ? entity.getInventory().getAvailableQuantity() : 0);
    }

    @Transactional(readOnly = true)
    public Product getProductEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
    }

    @Transactional
    public ProductResponseDTO insert(@Valid ProductInsertDTO dto) {
        Product entity = new Product();
        copyInsertDtoToEntity(dto, entity);
        repository.save(entity);

        InventoryResponseDTO inventory = inventoryService.createInitialInventory(
                entity,
                dto.initialQuantity(),
                dto.minQuantity()
        );
        
        return toResponseDTO(entity, inventory.availableQuantity());
    }

    @Transactional
    public ProductResponseDTO update(Long id, @Valid ProductUpdateDTO dto) {
        Product entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        InventoryResponseDTO inventory = inventoryService.findByProduct(id);

        copyUpdateDtoToEntity(dto, entity);
        entity = repository.save(entity);

        return toResponseDTO(entity, inventory.availableQuantity());
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
    public void activate(Long id) {
        setActive(id, true);
    }

    @Transactional
    public void deactivate(Long id) {
        setActive(id, false);
    }

    // helpers

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
    }

    private ProductResponseDTO toResponseDTO(Product product, Integer availableQuantity) {
        return new ProductResponseDTO(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.isActive(),
                availableQuantity
        );
    }

    private void setActive(Long id, boolean active) {
        Product entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        entity.setActive(active);
        repository.save(entity);
        eventPublisher.publishEvent(new ProductStatusChangedEvent(id, active));
    }

}
