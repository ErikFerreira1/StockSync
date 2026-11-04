package com.erikferreira.stocksync.dto.marketplaceListing;

import java.util.List;

public record PublicationResponseDTO(MarketplaceListingResponseDTO listing, List<String> warnings) {}
