package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.syncEvent.SyncEventInsertDTO;
import com.erikferreira.stocksync.dto.syncEvent.SyncEventResponseDTO;
import com.erikferreira.stocksync.entity.Order;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.SyncEvent;
import com.erikferreira.stocksync.entity.enums.SyncStatus;
import com.erikferreira.stocksync.repository.SyncEventRepository;

import com.erikferreira.stocksync.service.exceptions.InvalidSyncEventException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Validated
public class SyncEventService {

    private final SyncEventRepository repository;
    private final ProductService productService;
    private final SalesChannelService salesChannelService;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public Page<SyncEventResponseDTO> findHistoryByProduct(Long productId, Pageable pageable) {
        return repository.findByProductIdOrderByTimestampDesc(productId, pageable).map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<SyncEventResponseDTO> findFailedEvents(Pageable pageable) {
        return repository.findByStatus(SyncStatus.FAILURE, pageable).map(this::toResponseDTO);
    }
    @Transactional
    public SyncEventResponseDTO registerEvent(@Valid SyncEventInsertDTO dto) {
        validateStatusConsistency(dto);

        SalesChannel salesChannel = salesChannelService.getSalesChannelEntityById(dto.salesChannelId());

        Order order = dto.orderId() != null ? orderService.getOrderEntityById(dto.orderId()) : null;
        Product product = dto.productId() != null ? productService.getProductEntityById(dto.productId()) : null;


        SyncEvent entity = SyncEvent.builder().product(product).salesChannel(salesChannel).order(order).timestamp(LocalDateTime.now()).status(dto.status()).errorMessage(dto.errorMessage()).externalOrderId(dto.externalOrderId()).build();

        repository.save(entity);

        return toResponseDTO(entity);
    }

    @Transactional
    public void registerOrderSuccess(Long salesChannelId, Long orderId, String externalOrderId) {
        registerEvent(new SyncEventInsertDTO(
                null,
                salesChannelId,
                orderId,
                externalOrderId,
                SyncStatus.SUCCESS,
                null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerOrderFailure(Long salesChannelId, String externalOrderId, RuntimeException exception) {
        String errorMessage = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();

        if (errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500);
        }

        registerEvent(new SyncEventInsertDTO(
                null,
                salesChannelId,
                null,
                externalOrderId,
                SyncStatus.FAILURE,
                errorMessage));
    }

    @Transactional
    public void registerStockSuccess(Long productId, Long salesChannelId) {
        registerEvent(new SyncEventInsertDTO(
                productId,
                salesChannelId,
                null,
                null,
                SyncStatus.SUCCESS,
                null));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerStockFailure(Long productId, Long salesChannelId, RuntimeException exception) {

        String errorMessage = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();

        if (errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500);
        }

        registerEvent(new SyncEventInsertDTO(
                productId,
                salesChannelId,
                null,
                null,
                SyncStatus.FAILURE,
                errorMessage
        ));
    }

    // helpers

    private void validateStatusConsistency(SyncEventInsertDTO dto) {
        boolean hasErrorMessage = dto.errorMessage() != null && !dto.errorMessage().isBlank();

        if (dto.status() == SyncStatus.FAILURE && !hasErrorMessage) {
            throw new InvalidSyncEventException("errorMessage is required when status is FAILURE");
        }

        if (dto.status() != SyncStatus.FAILURE && hasErrorMessage) {
            throw new InvalidSyncEventException("errorMessage must only be provided when status is FAILURE");
        }
    }

    private SyncEventResponseDTO toResponseDTO(SyncEvent syncEvent) {
        Long orderId = syncEvent.getOrder() != null ? syncEvent.getOrder().getId() : null;
        Long productId = syncEvent.getProduct() != null ? syncEvent.getProduct().getId() : null;

        return new SyncEventResponseDTO(syncEvent.getId(), productId, syncEvent.getSalesChannel().getId(), orderId, syncEvent.getExternalOrderId(), syncEvent.getTimestamp(), syncEvent.getStatus(), syncEvent.getErrorMessage(), syncEvent.getAttempts());
    }
}
