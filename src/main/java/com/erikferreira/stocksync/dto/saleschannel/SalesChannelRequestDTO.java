package com.erikferreira.stocksync.dto.saleschannel;

import com.erikferreira.stocksync.entity.enums.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record SalesChannelRequestDTO(
        @NotBlank(message = "Name cannot be blank")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Channel type cannot be null")
        ChannelType type,

        @URL(message = "Base URL must be a valid URL")
        @Size(max = 500, message = "Base URL must be at most 500 characters")
        String baseUrl

) {
}
