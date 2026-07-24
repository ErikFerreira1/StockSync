package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.inventory.AdjustStockDTO;
import com.erikferreira.stocksync.dto.inventory.StockRequestDTO;
import com.erikferreira.stocksync.dto.stockmovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.dto.stockmovement.StockMovementResponseDTO;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.StockMovement;
import com.erikferreira.stocksync.entity.enums.OriginType;
import com.erikferreira.stocksync.repository.ProductRepository;
import com.erikferreira.stocksync.repository.StockMovementRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidMovementOriginException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Validated
public class StockMovementService {

    private final StockMovementRepository repository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;


    @Transactional
    public StockMovementResponseDTO registerMovement(@Valid StockMovementInsertDTO dto) {
        validateOrigin(dto);

        Product product = productRepository.findById(dto.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        StockMovement stockMovement = StockMovement.builder()
                .product(product)
                .quantity(dto.quantity())
                .type(dto.type())
                .originType(dto.originType())
                .originId(dto.originId())
                .note(dto.note())
                .occurredAt(LocalDateTime.now())
                .build();

        repository.save(stockMovement);
        applyToInventory(dto);

        return toResponseDTO(stockMovement);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponseDTO> findHistoryByProduct(Long productId, Pageable pageable) {
        return repository.findByProductId(productId, pageable)
                .map(this::toResponseDTO);
    }


    private void validateOrigin(StockMovementInsertDTO dto) {
        switch (dto.type()) {
            case SALE, CANCELLATION, REFUND -> {
                if (dto.originType() != OriginType.ORDER || dto.originId() == null) {
                    throw new InvalidMovementOriginException(
                            "Movement type " + dto.type() + " requires originType=ORDER and a valid originId");
                }
            }
            case MANUAL_INCREASE, MANUAL_DECREASE -> {
                if (dto.originType() != null || dto.originId() != null) {
                    throw new InvalidMovementOriginException(
                            "Movement type " + dto.type() + " must not have an origin");
                }
            }
            case RECONCILIATION_CORRECTION -> {
                if (dto.originType() != OriginType.RECONCILIATION) {
                    throw new InvalidMovementOriginException(
                            "Movement type " + dto.type() + " requires originType=RECONCILIATION");
                }
            }
        }
    }

    private void applyToInventory(StockMovementInsertDTO dto) {
        switch (dto.type()) {
            case SALE, MANUAL_DECREASE ->
                    inventoryService.decreaseStock(new StockRequestDTO(dto.productId(), dto.quantity()));

            case CANCELLATION, REFUND, MANUAL_INCREASE ->
                    inventoryService.increaseStock(new StockRequestDTO(dto.productId(), dto.quantity()));

            case RECONCILIATION_CORRECTION ->
                    inventoryService.adjustStock(new AdjustStockDTO(dto.productId(), dto.quantity()));
        }
    }

    private StockMovementResponseDTO toResponseDTO(StockMovement stockMovement) {
        return new StockMovementResponseDTO(
                stockMovement.getId(),
                stockMovement.getProduct().getId(),
                stockMovement.getOccurredAt(),
                stockMovement.getQuantity(),
                stockMovement.getType(),
                stockMovement.getOriginType(),
                stockMovement.getOriginId(),
                stockMovement.getNote()
        );
    }
}


