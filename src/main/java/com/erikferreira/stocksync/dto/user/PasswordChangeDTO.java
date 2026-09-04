package com.erikferreira.stocksync.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeDTO(

        @NotBlank(message = "password is required")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String currentPassword,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password must be between 8 and 64 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String newPassword
) {
}
