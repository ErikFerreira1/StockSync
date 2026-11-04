package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.marketplaceListing.*;
import com.erikferreira.stocksync.entity.*;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.integration.MarketplacePublicationPort;
import com.erikferreira.stocksync.integration.MarketplacePublicationPort.PublishedListing;
import com.erikferreira.stocksync.service.exceptions.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@Profile("!demo")
@Validated
@RequiredArgsConstructor
public class MarketplacePublicationService {
    private final MarketplacePublicationPort publicationPort;
    private final ProductService productService;
    private final SalesChannelService salesChannelService;
    private final InventoryService inventoryService;
    private final MarketplaceListingService listingService;
    private final PublicationJournalService journal;
    private final ObjectMapper objectMapper;
    private final StockSynchronizationService stockSynchronizationService;

    public List<PublicationCategoryDTO> searchCategories(Long channelId, String query) {
        activeChannel(channelId);
        return publicationPort.searchCategories(channelId, query);
    }

    public PublicationOptionsDTO options(Long channelId, String categoryId) {
        activeChannel(channelId);
        return publicationPort.getOptions(channelId, categoryId);
    }

    public PublicationValidationResponseDTO validate(@Valid PublicationRequestDTO request) {
        return new PublicationValidationResponseDTO(publicationPort.validate(request, prepare(request)));
    }

    public PublicationResponseDTO publish(@Valid PublicationRequestDTO request) {
        String hash = fingerprint(request);
        var existing = journal.find(request.requestId());
        if (existing.isPresent()) return resume(request, hash, existing.get());
        int quantity = prepare(request);
        publicationPort.validate(request, quantity);
        journal.reserve(request, hash);
        PublishedListing published;
        try {
            published = publicationPort.publish(request, quantity);
        } catch (PublicationValidationException exception) {
            journal.rejected(request.requestId());
            throw exception;
        }
        // Keep STARTED for timeouts/unknown outcomes: repeating POST /items could duplicate a live ad.
        journal.created(request.requestId(), published);
        return finish(request, published, quantity);
    }

    private PublicationResponseDTO resume(PublicationRequestDTO request, String hash, MarketplacePublication publication) {
        if (!hash.equals(publication.getRequestHash())) {
            throw new PublicationConflictException("Request ID was already used with different publication data");
        }
        if (publication.getListingId() != null) {
            return new PublicationResponseDTO(listingService.findById(publication.getListingId()),
                    warnings(request, publication.isDescriptionSaved()));
        }
        if (publication.getExternalId() != null) {
            return finish(request, new PublishedListing(publication.getExternalId(),
                    publication.getExternalUrl(), publication.getExternalStatus()), null);
        }
        throw new PublicationConflictException("Publication is pending or was rejected; check Mercado Livre before creating another request");
    }

    private PublicationResponseDTO finish(PublicationRequestDTO request, PublishedListing published, Integer initialQuantity) {
        // Persist the remote identity before optional follow-up requests. Never create a second item on retry.
        MarketplaceListingResponseDTO listing = listingService.registerPublished(request.productId(), request.salesChannelId(), published);
        // A stock event may have committed before this new link became visible. Reconcile that gap.
        if (initialQuantity == null || !initialQuantity.equals(inventoryService.getAvailableQuantity(request.productId()))) {
            stockSynchronizationService.synchronizeProductStock(request.productId());
        }
        boolean descriptionSaved = request.description() == null || request.description().isBlank();
        if (!descriptionSaved) {
            try {
                publicationPort.addDescription(request.salesChannelId(), published.id(), request.description());
                descriptionSaved = true;
            } catch (RuntimeException ignored) { /* Item exists; report a partial success instead of republishing. */ }
        }
        journal.linked(request.requestId(), listing.id(), descriptionSaved);
        return new PublicationResponseDTO(listing, warnings(request, descriptionSaved));
    }

    private List<String> warnings(PublicationRequestDTO request, boolean descriptionSaved) {
        return !descriptionSaved && request.description() != null && !request.description().isBlank()
                ? List.of("description_not_saved") : List.of();
    }

    private int prepare(PublicationRequestDTO request) {
        activeChannel(request.salesChannelId());
        Product product = productService.getProductEntityById(request.productId());
        if (!product.isActive()) throw invalid("Inactive products cannot be published");
        int quantity = inventoryService.getAvailableQuantity(product.getId());
        if (quantity <= 0) throw invalid("Product must have available stock");
        PublicationOptionsDTO options = publicationPort.getOptions(request.salesChannelId(), request.categoryId());
        if (request.name().length() > options.maxNameLength()) throw invalid("Publication name exceeds category limit");
        if (request.pictures().size() > options.maxPictures()) throw invalid("Too many pictures for this category");
        if (options.listingTypes().stream().noneMatch(t -> t.id().equals(request.listingTypeId()))) {
            throw invalid("Listing type is not available for this account and category");
        }
        if (!options.shippingModes().contains(request.shippingMode())) throw invalid("Shipping mode is not available");
        if (request.localPickUp() && !options.localPickUpAllowed()) throw invalid("Local pick-up is not available");
        checkFields(options.attributes(), request.attributes());
        checkFields(options.saleTerms(), request.saleTerms());
        return quantity;
    }

    private void checkFields(List<PublicationOptionsDTO.Field> fields, List<PublicationRequestDTO.Attribute> values) {
        Set<String> seen = new HashSet<>();
        for (var value : values) {
            if (!seen.add(value.id())) throw invalid("Duplicate attribute: " + value.id());
            var field = fields.stream().filter(f -> f.id().equals(value.id())).findFirst()
                    .orElseThrow(() -> invalid("Unknown attribute: " + value.id()));
            if (value.valueId() != null && !value.valueId().isBlank()
                    && field.values().stream().noneMatch(v -> v.id().equals(value.valueId()))) {
                throw invalid("Invalid value for attribute: " + value.id());
            }
        }
        for (var field : fields) if (field.required() && !seen.contains(field.id())) {
            throw invalid("Required attribute: " + field.id());
        }
    }

    private void activeChannel(Long channelId) {
        SalesChannel channel = salesChannelService.getSalesChannelEntityById(channelId);
        if (!channel.isActive() || channel.getType() != ChannelType.MERCADO_LIVRE) {
            throw new InvalidSalesChannelException("An active Mercado Livre channel is required");
        }
    }

    private String fingerprint(PublicationRequestDTO request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Could not fingerprint publication request");
        }
    }

    private static PublicationValidationException invalid(String issue) {
        return new PublicationValidationException(List.of(issue));
    }
}
