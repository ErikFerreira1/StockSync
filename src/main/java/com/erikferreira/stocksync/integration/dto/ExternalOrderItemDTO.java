package com.erikferreira.stocksync.integration.dto;

import java.math.BigDecimal;

public record ExternalOrderItemDTO (
        String listingId,
        Integer quantity,
        BigDecimal unitPrice
) {}
