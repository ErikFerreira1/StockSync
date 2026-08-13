package com.erikferreira.stocksync.integration.dto.mercadoLivre;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record MercadoLivreOrderItemResponseDTO(
        MercadoLivreOrderItemListingDTO item,
        Integer quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice
) {
}
