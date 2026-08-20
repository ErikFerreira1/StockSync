package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingInsertDTO;
import com.erikferreira.stocksync.dto.marketplaceListing.MarketplaceListingResponseDTO;
import com.erikferreira.stocksync.service.MarketplaceListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/marketplace-listings")
public class MarketplaceListingController {

    private final MarketplaceListingService service;

    @GetMapping
    public ResponseEntity<Page<MarketplaceListingResponseDTO>> findAllPaged(Pageable pageable) {
        return ResponseEntity.ok(service.findAllPaged(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MarketplaceListingResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<MarketplaceListingResponseDTO> insert(
            @Valid @RequestBody MarketplaceListingInsertDTO insertDTO) {
        MarketplaceListingResponseDTO dto = service.insert(insertDTO);

        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(dto.id())
                .toUri();

        return ResponseEntity.created(uri).body(dto);
    }
}
