package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.demo.DemoMarketplaceListingResponseDTO;
import com.erikferreira.stocksync.integration.adapter.DemoMarketplaceAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("demo")
@RequiredArgsConstructor
@RequestMapping("/demo/marketplace-listings")
@Tag(name = "Demo marketplace", description = "Simulated Mercado Livre state used by the public demo")
public class DemoMarketplaceController {

    private final DemoMarketplaceAdapter demoMarketplaceAdapter;

    @GetMapping
    @Operation(summary = "List the simulated Mercado Livre listings")
    public ResponseEntity<List<DemoMarketplaceListingResponseDTO>> findAll() {
        return ResponseEntity.ok(demoMarketplaceAdapter.findAllListings());
    }

    @GetMapping("/{listingId}")
    @Operation(summary = "Inspect a simulated Mercado Livre listing")
    public ResponseEntity<DemoMarketplaceListingResponseDTO> findById(@PathVariable String listingId) {
        return ResponseEntity.ok(demoMarketplaceAdapter.findListingById(listingId));
    }
}
