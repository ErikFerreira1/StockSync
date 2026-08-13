package com.erikferreira.stocksync.integration.dto.mercadoLivre;

import java.util.List;

public record MercadoLivreOrdersSearchResponseDTO(
        List<MercadoLivreOrderResponseDTO> results
) {
}
