package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.demo.DemoMarketplaceListingResponseDTO;
import com.erikferreira.stocksync.dto.demo.DemoOrderRequestDTO;
import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.adapter.DemoMarketplaceAdapter;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidOrderStatusException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("demo")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemoOrderService {
    private final MarketplaceListingRepository repository;
    private final DemoMarketplaceAdapter adapter;

    public List<DemoMarketplaceListingResponseDTO> listings(Long channelId) {
        return repository.findBySalesChannelId(channelId).stream()
                .filter(this::eligible)
                .map(listing -> adapter.findListingById(listing.getListingId()))
                .filter(listing -> listing.status() == ListingStatus.ACTIVE && listing.availableQuantity() > 0)
                .toList();
    }

    public ExternalOrderDTO simulate(DemoOrderRequestDTO request) {
        MarketplaceListing listing = repository.findByListingIdAndSalesChannelId(request.listingId(), request.salesChannelId())
                .orElseThrow(() -> new MarketplaceListingNotFoundException("Demo listing not found in this sales channel"));
        if (!eligible(listing)) {
            throw new InvalidOrderStatusException("The demo listing, product and Mercado Livre channel must be active");
        }
        return adapter.simulateSale(request.salesChannelId(), request.listingId(), request.quantity());
    }

    private boolean eligible(MarketplaceListing listing) {
        return listing.getStatus() == ListingStatus.ACTIVE && listing.getProduct().isActive()
                && listing.getSalesChannel().isActive()
                && listing.getSalesChannel().getType() == ChannelType.MERCADO_LIVRE;
    }
}
