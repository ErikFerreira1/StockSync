package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.marketplaceListing.*;
import com.erikferreira.stocksync.service.MarketplacePublicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/marketplace-publications")
@Profile("!demo")
@RequiredArgsConstructor
@Validated
public class MarketplacePublicationController {
    private final MarketplacePublicationService service;

    @GetMapping("/categories")
    public List<PublicationCategoryDTO> searchCategories(@RequestParam @Positive Long salesChannelId,
            @RequestParam @NotBlank @Size(max = 120) String query) {
        return service.searchCategories(salesChannelId, query);
    }

    @GetMapping("/options")
    public PublicationOptionsDTO options(@RequestParam @Positive Long salesChannelId,
            @RequestParam @Pattern(regexp = "MLB[0-9]+") String categoryId) {
        return service.options(salesChannelId, categoryId);
    }

    @PostMapping("/validate")
    public ResponseEntity<Void> validate(@Valid @RequestBody PublicationRequestDTO request) {
        service.validate(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<PublicationResponseDTO> publish(@Valid @RequestBody PublicationRequestDTO request) {
        PublicationResponseDTO response = service.publish(request);
        return ResponseEntity.created(URI.create("/marketplace-listings/" + response.listing().id())).body(response);
    }
}
