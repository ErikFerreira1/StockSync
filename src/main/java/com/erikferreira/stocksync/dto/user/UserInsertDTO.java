package com.erikferreira.stocksync.dto.user;

import com.erikferreira.stocksync.entity.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserInsertDTO(

        @NotBlank(message = "username is required")
        @Size(min = 3, max = 100, message = "username must be between 3 and 100 characters")
        @Pattern(regexp = "^[a-z0-9._-]+$", message = "username contains invalid characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 64, message = "password must be between 8 and 64 characters")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password,

        @NotNull(message = "role is required")
        UserRole role
) {
}
