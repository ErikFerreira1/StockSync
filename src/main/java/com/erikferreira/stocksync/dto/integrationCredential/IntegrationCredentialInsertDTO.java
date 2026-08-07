package com.erikferreira.stocksync.dto.integrationCredential;

import jakarta.validation.constraints.*;

public record IntegrationCredentialInsertDTO(
        @NotNull(message = "salesChannelId is required")
        Long salesChannelId,

        @NotBlank(message = "clientId is required")
        String clientId,

        @NotBlank(message = "clientSecret is required")
        String clientSecret
) {
}
