package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.inventory.AdjustStockDTO;
import com.erikferreira.stocksync.dto.inventory.InventoryResponseDTO;
import com.erikferreira.stocksync.dto.inventory.StockRequestDTO;
import com.erikferreira.stocksync.dto.inventory.UpdateMinQuantityDTO;
import com.erikferreira.stocksync.entity.Inventory;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.repository.InventoryRepository;
import com.erikferreira.stocksync.service.exceptions.InsufficientStockException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class InventoryService {

    private final InventoryRepository repository;

    @Transactional(readOnly = true)
    public InventoryResponseDTO findByProduct(Long productId) {
        return toResponseDTO(findInventoryOrThrow(productId));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponseDTO> findProductsBelowMinimum() {
        return repository.findAllBelowMinimum().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Integer getAvailableQuantity(Long productId) {
        Inventory inventory = findInventoryOrThrow(productId);

        return inventory.getAvailableQuantity();
    }

    @Transactional
    public InventoryResponseDTO createInitialInventory(Product product, Integer initialQuantity, Integer minQuantity) {
        Inventory inventory = Inventory.builder()
                .product(product)
                .availableQuantity(initialQuantity)
                .minQuantity(minQuantity)
                .updatedAt(LocalDateTime.now())
                .build();

        return toResponseDTO(repository.save(inventory));
    }

    @Transactional
    public void increaseStock(@Valid StockRequestDTO request) {
        Inventory inventory = findInventoryOrThrow(request.productId());
        int newQuantity = inventory.getAvailableQuantity() + request.quantity();

        applyNewQuantity(inventory, newQuantity, "increase");
    }

    @Transactional
    public void decreaseStock(@Valid StockRequestDTO request) {
        Inventory inventory = findInventoryOrThrow(request.productId());

        if (inventory.getAvailableQuantity() < request.quantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock. Available: " + inventory.getAvailableQuantity() +
                            ", Requested: " + request.quantity()
            );
        }

        int newQuantity = inventory.getAvailableQuantity() - request.quantity();

        applyNewQuantity(inventory, newQuantity, "decrease");
    }

    @Transactional
    public void adjustStock(@Valid AdjustStockDTO request) {
        Inventory inventory = findInventoryOrThrow(request.productId());
        Integer currentQuantity = inventory.getAvailableQuantity();
        Integer newQuantity = request.quantity();

        if (currentQuantity.equals(newQuantity)) {
            log.info("No adjustment needed for product {}: current stock = {}", request.productId(), currentQuantity);
            toResponseDTO(inventory);
            return;
        }

        applyNewQuantity(inventory, newQuantity, "adjust");
    }

    @Transactional
    public InventoryResponseDTO updateMinQuantity(Long productId, @Valid UpdateMinQuantityDTO request) {
        Inventory inventory = findInventoryOrThrow(productId);
        inventory.setMinQuantity(request.minQuantity());
        inventory.setUpdatedAt(LocalDateTime.now());

        repository.save(inventory);

        return toResponseDTO(inventory);
    }

    // helpers

    private Inventory findInventoryOrThrow(Long productId) {
        return repository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
    }

    private InventoryResponseDTO applyNewQuantity(Inventory inventory, int newQuantity, String operation) {
        int previousQuantity = inventory.getAvailableQuantity();

        inventory.setAvailableQuantity(newQuantity);
        inventory.setUpdatedAt(LocalDateTime.now());
        repository.save(inventory);

        log.info("Stock {} for product {}: {} -> {}",
                operation, inventory.getProduct().getId(), previousQuantity, newQuantity);

        if (inventory.getMinQuantity() != null && newQuantity <= inventory.getMinQuantity()) {
            log.warn("Stock for product {} at or below minimum threshold: {} <= {}",
                    inventory.getProduct().getId(), newQuantity, inventory.getMinQuantity());
        }

        return toResponseDTO(inventory);
    }

    private InventoryResponseDTO toResponseDTO(Inventory inventory) {
        return new InventoryResponseDTO(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getAvailableQuantity(),
                inventory.getMinQuantity(),
                inventory.getUpdatedAt()
        );
    }
}

