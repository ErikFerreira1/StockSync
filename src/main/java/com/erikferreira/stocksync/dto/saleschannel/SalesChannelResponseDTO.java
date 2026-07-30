package com.erikferreira.stocksync.dto.saleschannel;

import com.erikferreira.stocksync.entity.enums.ChannelType;

public record SalesChannelResponseDTO(
        Long id,
        String name,
        ChannelType type,
        String baseUrl,
        boolean active
) {
}
