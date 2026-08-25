package com.erikferreira.stocksync.integration.dto;

import java.time.Instant;
import java.util.List;

public record ExternalOrderDTO (
        String externalOrderId,
        Instant orderDate,
        String status,
        List<ExternalOrderItemDTO> items
) {}
