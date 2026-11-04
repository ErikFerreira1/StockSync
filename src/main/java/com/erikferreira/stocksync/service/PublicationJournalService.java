package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.marketplaceListing.PublicationRequestDTO;
import com.erikferreira.stocksync.entity.MarketplacePublication;
import com.erikferreira.stocksync.entity.enums.PublicationState;
import com.erikferreira.stocksync.integration.MarketplacePublicationPort.PublishedListing;
import com.erikferreira.stocksync.repository.MarketplacePublicationRepository;
import com.erikferreira.stocksync.service.exceptions.PublicationConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicationJournalService {
    private final MarketplacePublicationRepository repository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<MarketplacePublication> find(UUID requestId) { return repository.findById(requestId); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(PublicationRequestDTO request, String hash) {
        if (repository.reserve(request.requestId(), hash, request.productId(), request.salesChannelId()) != 1) {
            throw new PublicationConflictException(
                    "Publication is already being processed; reuse the same request ID");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void created(UUID id, PublishedListing listing) {
        MarketplacePublication publication = repository.findById(id).orElseThrow();
        publication.setExternalId(listing.id());
        publication.setExternalUrl(listing.url());
        publication.setExternalStatus(listing.status());
        publication.setState(PublicationState.CREATED);
        repository.saveAndFlush(publication);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(UUID id) {
        MarketplacePublication publication = repository.findById(id).orElseThrow();
        publication.setState(PublicationState.REJECTED);
        repository.save(publication);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void linked(UUID id, Long listingId, boolean descriptionSaved) {
        MarketplacePublication publication = repository.findById(id).orElseThrow();
        publication.setListingId(listingId);
        publication.setDescriptionSaved(descriptionSaved);
        publication.setState(PublicationState.LINKED);
        repository.save(publication);
    }
}
