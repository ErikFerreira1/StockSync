package com.erikferreira.stocksync.dto.integrationCredential;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record IntegrationCredentialTokenUpdateDTO(
        @NotBlank(message = "accessToken is required")
        String accessToken,

        @NotBlank(message = "refreshToken is required")
        String refreshToken,

        @NotNull(message = "expiresAt is required")
        LocalDateTime expiresAt
) {
}
