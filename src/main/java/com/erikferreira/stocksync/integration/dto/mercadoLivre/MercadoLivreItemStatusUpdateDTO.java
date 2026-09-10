package com.erikferreira.stocksync.integration.dto.mercadoLivre;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MercadoLivreItemStatusUpdateDTO(
        String status,
        @JsonProperty("available_quantity") Integer availableQuantity
) {
}
