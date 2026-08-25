package com.erikferreira.stocksync.dto.integrationCredential;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record IntegrationCredentialTokenUpdateDTO(
        @NotBlank(message = "accessToken is required")
        String accessToken,

        @NotBlank(message = "refreshToken is required")
        String refreshToken,

        @NotNull(message = "expiresAt is required")
        Instant expiresAt
) {
}
