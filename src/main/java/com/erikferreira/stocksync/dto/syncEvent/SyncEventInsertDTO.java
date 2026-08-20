package com.erikferreira.stocksync.dto.syncEvent;

import com.erikferreira.stocksync.entity.enums.SyncStatus;
import jakarta.validation.constraints.*;

public record SyncEventInsertDTO(

        Long productId,

        @NotNull(message = "salesChannelId is required")
        Long salesChannelId,

        Long orderId,

        @Size(max = 255, message = "externalOrderId must be at most 255 characters")
        String externalOrderId,

        @NotNull(message = "status is required")
        SyncStatus status,

        @Size(max = 500, message = "errorMessage must be at most 500 characters")
        String errorMessage

) {}
