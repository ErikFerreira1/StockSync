package com.erikferreira.stocksync.integration.dto.mercadoLivre;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public record MercadoLivreOrderResponseDTO(
        Long id,
        String status,
        @JsonProperty("date_created") OffsetDateTime dateCreated, // time zone
        @JsonProperty("order_items") List<MercadoLivreOrderItemResponseDTO> orderItems
) {
}
