package com.erikferreira.stocksync.dto.auth;

import com.erikferreira.stocksync.dto.user.UserResponseDTO;

import java.time.Instant;

public record LoginResponseDTO(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponseDTO user
) {
}
