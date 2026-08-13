package com.erikferreira.stocksync.integration.dto.mercadoLivre;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MercadoLivreItemResponseDTO(
        @JsonProperty("available_quantity") Integer availableQuantity
) {
}
