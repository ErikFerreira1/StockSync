package com.erikferreira.stocksync.dto.integrationCredential;

import jakarta.validation.constraints.NotBlank;

public record IntegrationCredentialRequestDTO(
        @NotBlank(message = "clientId is required")
        String clientId,

        @NotBlank(message = "clientSecret is required")
        String clientSecret
) {}
