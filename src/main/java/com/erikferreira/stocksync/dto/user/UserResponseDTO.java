package com.erikferreira.stocksync.dto.user;

import com.erikferreira.stocksync.entity.enums.UserRole;

import java.time.Instant;

public record UserResponseDTO(
        Long id,
        String username,
        UserRole role,
        boolean active,
        Instant createdAt
) {}
