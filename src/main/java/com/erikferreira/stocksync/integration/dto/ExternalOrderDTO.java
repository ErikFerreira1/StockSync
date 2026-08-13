package com.erikferreira.stocksync.integration.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExternalOrderDTO (
        String externalOrderId,
        LocalDateTime orderDate,
        String status,
        List<ExternalOrderItemDTO> items
) {}
