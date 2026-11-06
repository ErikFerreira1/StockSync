package com.erikferreira.stocksync.dto.syncEvent;

import com.erikferreira.stocksync.entity.enums.SyncStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SyncEventFilterDTO(SyncStatus status,
        @Positive Long productId,
        @Positive Long salesChannelId,
        @Positive Long orderId,
        @Size(max = 255) String externalOrderId,
        @Pattern(regexp = "PRODUCT|ORDER") String scope) {}
