package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingResponseDTO;
import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.repository.MarketplaceListingRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidSalesChannelException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingAlreadyExistsException;
import com.erikferreira.stocksync.service.exceptions.MarketplaceListingNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class MarketplaceListingService {

    private final MarketplaceListingRepository repository;
    private final ProductService productService;
    private final SalesChannelService salesChannelService;


    @Transactional(readOnly = true)
    public Page<MarketplaceListingResponseDTO> findAllPaged(Pageable pageable) {
        Page<MarketplaceListing> marketplaceListings = repository.findAll(pageable);

        return marketplaceListings.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MarketplaceListingResponseDTO findById(Long id) {
        MarketplaceListing marketplaceListing = repository.findById(id)
                .orElseThrow(() -> new MarketplaceListingNotFoundException("Marketplace listing not found with id " + id));

        return toResponse(marketplaceListing);
    }


    @Transactional
    public MarketplaceListingResponseDTO insert(@Valid MarketplaceListingInsertDTO dto) {
        Product product = productService.getProductEntityById(dto.productId());
        SalesChannel salesChannel = salesChannelService.getSalesChannelEntityById(dto.salesChannelId());

        if (salesChannel.getType() != ChannelType.MERCADO_LIVRE) {
            throw new InvalidSalesChannelException(
                    "Only Mercado Livre listings are currently supported");
        }

        boolean alreadyExists = repository.existsBySalesChannelIdAndListingId(dto.salesChannelId(), dto.listingId());

        if (alreadyExists) {
            throw new MarketplaceListingAlreadyExistsException("Listing already exists for this sales channel: " + dto.listingId());
        }

        MarketplaceListing listing = MarketplaceListing.builder()
                .product(product)
                .salesChannel(salesChannel)
                .listingId(dto.listingId())
                .listingUrl(dto.listingUrl())
                .status(dto.status())
                .lastSyncedAt(null)
                .build();

        repository.save(listing);

        return toResponse(listing);
    }

    @Transactional(readOnly = true)
    public MarketplaceListing getByListingIdAndSalesChannelId(String listingId, Long salesChannelId) {
        return repository.findByListingIdAndSalesChannelId(listingId, salesChannelId)
                .orElseThrow(() -> new MarketplaceListingNotFoundException(
                        "Marketplace listing not found with listingId " + listingId
                                + " and salesChannelId " + salesChannelId));
    }

    //helpers

    private MarketplaceListingResponseDTO toResponse(MarketplaceListing listing) {
        return new MarketplaceListingResponseDTO(
                listing.getId(),
                listing.getProduct().getId(),
                listing.getSalesChannel().getId(),
                listing.getListingId(),
                listing.getListingUrl(),
                listing.getStatus(),
                listing.getLastSyncedAt()
        );
    }


}
