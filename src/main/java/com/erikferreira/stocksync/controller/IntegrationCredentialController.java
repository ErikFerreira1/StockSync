package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialInsertDTO;
import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialResponseDTO;
import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.service.IntegrationCredentialService;
import com.erikferreira.stocksync.service.TokenRefreshService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/integration-credentials")
public class IntegrationCredentialController {

    private final IntegrationCredentialService service;
    private final TokenRefreshService tokenRefreshService;

    @GetMapping("/{id}")
    public ResponseEntity<IntegrationCredentialResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/sales-channel/{salesChannelId}")
    public ResponseEntity<IntegrationCredentialResponseDTO> findBySalesChannelId(@PathVariable Long salesChannelId) {
        return ResponseEntity.ok(service.findBySalesChannelId(salesChannelId));
    }

    @GetMapping("/{salesChannelId}/valid-token") // delete later
    public ResponseEntity<String> getValidAccessToken(@PathVariable Long salesChannelId) {
        return ResponseEntity.ok(tokenRefreshService.getValidAccessToken(salesChannelId));
    }

    @PostMapping
    public ResponseEntity<IntegrationCredentialResponseDTO> register(@Valid @RequestBody IntegrationCredentialInsertDTO dto) {
        IntegrationCredentialResponseDTO response = service.registerCredential(dto);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @PatchMapping("/{salesChannelId}/token")
    public ResponseEntity<IntegrationCredentialResponseDTO> updateToken(@PathVariable Long salesChannelId,
                                                                        @Valid @RequestBody IntegrationCredentialTokenUpdateDTO dto) {
        return ResponseEntity.ok(service.updateToken(salesChannelId, dto));
    }

}
