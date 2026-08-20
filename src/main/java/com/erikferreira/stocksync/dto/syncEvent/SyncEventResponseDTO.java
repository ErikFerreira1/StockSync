package com.erikferreira.stocksync.dto.syncEvent;

import com.erikferreira.stocksync.entity.enums.SyncStatus;

import java.time.LocalDateTime;

public record SyncEventResponseDTO(
        Long id,
        Long productId,
        Long salesChannelId,
        Long orderId,
        String externalOrderId,
        LocalDateTime occurredAt,
        SyncStatus status,
        String errorMessage,
        Integer attempts
)
{}
