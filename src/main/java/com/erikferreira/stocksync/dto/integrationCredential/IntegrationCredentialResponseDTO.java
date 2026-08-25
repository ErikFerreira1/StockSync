package com.erikferreira.stocksync.dto.integrationCredential;

import java.time.Instant;

public record IntegrationCredentialResponseDTO(
        Long id,
        Long salesChannelId,
        String clientId,
        Instant expiresAt,
        boolean connected
) {
}
