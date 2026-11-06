package com.erikferreira.stocksync.dto.demo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DemoOrderRequestDTO(
        @NotNull @Positive Long salesChannelId,
        @NotBlank String listingId,
        @NotNull @Min(1) Integer quantity) {}
