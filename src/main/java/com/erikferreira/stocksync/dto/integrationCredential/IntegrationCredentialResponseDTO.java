package com.erikferreira.stocksync.dto.integrationCredential;

import java.time.LocalDateTime;

public record IntegrationCredentialResponseDTO(
        Long id,
        Long salesChannelId,
        String clientId,
        LocalDateTime expiresAt,
        boolean connected
) {
}
