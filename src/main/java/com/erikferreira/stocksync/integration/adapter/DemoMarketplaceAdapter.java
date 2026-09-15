package com.erikferreira.stocksync.integration.adapter;

import com.erikferreira.stocksync.dto.demo.DemoMarketplaceListingResponseDTO;
import com.erikferreira.stocksync.entity.enums.ListingStatus;
import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("demo")
public class DemoMarketplaceAdapter implements MarketplaceIntegrationPort {

    private final Map<String, DemoListing> listings = new ConcurrentHashMap<>();

    @Override
    public List<ExternalOrderDTO> fetchNewOrders(Long salesChannelId) {
        return List.of();
    }

    @Override
    public void updateStock(String listingId, Integer quantity) {
        listings.compute(listingId, (id, listing) -> requireListing(id, listing).withQuantity(quantity));
    }

    @Override
    public void updateListingStatus(
            String listingId,
            ListingStatus status,
            Integer availableQuantity) {
        listings.compute(listingId, (id, listing) -> requireListing(id, listing)
                .withStatus(status, availableQuantity));
    }

    @Override
    public Integer getCurrentStock(String listingId) {
        return findListing(listingId).availableQuantity();
    }

    public void registerListing(
            String listingId,
            Long productId,
            String title,
            BigDecimal price,
            Integer availableQuantity,
            ListingStatus status) {
        listings.put(listingId, new DemoListing(
                listingId,
                productId,
                title,
                price,
                availableQuantity,
                status,
                Instant.now()));
    }

    public List<DemoMarketplaceListingResponseDTO> findAllListings() {
        return listings.values().stream()
                .sorted(Comparator.comparing(DemoListing::listingId))
                .map(DemoListing::toResponse)
                .toList();
    }

    public DemoMarketplaceListingResponseDTO findListingById(String listingId) {
        return findListing(listingId).toResponse();
    }

    private DemoListing findListing(String listingId) {
        return requireListing(listingId, listings.get(listingId));
    }

    private DemoListing requireListing(String listingId, DemoListing listing) {
        if (listing == null) {
            throw new MarketplaceListingNotFoundException(
                    "Demo marketplace listing not found with id " + listingId);
        }
        return listing;
    }

    private record DemoListing(
            String listingId,
            Long productId,
            String title,
            BigDecimal price,
            Integer availableQuantity,
            ListingStatus status,
            Instant updatedAt) {

        private DemoListing withQuantity(Integer quantity) {
            return new DemoListing(
                    listingId,
                    productId,
                    title,
                    price,
                    quantity,
                    status,
                    Instant.now());
        }

        private DemoListing withStatus(ListingStatus newStatus, Integer quantity) {
            return new DemoListing(
                    listingId,
                    productId,
                    title,
                    price,
                    quantity != null ? quantity : availableQuantity,
                    newStatus,
                    Instant.now());
        }

        private DemoMarketplaceListingResponseDTO toResponse() {
            return new DemoMarketplaceListingResponseDTO(
                    listingId,
                    productId,
                    title,
                    price,
                    availableQuantity,
                    status,
                    updatedAt);
        }
    }
}
