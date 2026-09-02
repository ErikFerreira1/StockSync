package com.erikferreira.stocksync.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password
) {
}
