package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.dto.integrationCredential.MercadoLivreTokenResponseDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.service.IntegrationCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class MercadoLivreAuthController {

    private final IntegrationCredentialService credentialService;
    private final RestClient mercadoLivreRestClient;

    @Value("${app.mercadolivre.redirect-uri}")
    private String redirectUri;

    @GetMapping("/mercadolivre/authorize/{salesChannelId}")
    public ResponseEntity<Void> authorize(@PathVariable Long salesChannelId) {
        IntegrationCredential credential = credentialService.getCredentialEntityBySalesChannelId(salesChannelId);

        String url = "https://auth.mercadolivre.com.br/authorization"
                + "?response_type=code"
                + "&client_id=" + credential.getClientId()
                + "&redirect_uri=" + redirectUri
                + "&state=" + salesChannelId;

        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @GetMapping("/mercadolivre/callback")
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state) {
        Long salesChannelId = Long.valueOf(state);
        IntegrationCredential credential = credentialService.getCredentialEntityBySalesChannelId(salesChannelId);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("client_id", credential.getClientId());
        map.add("client_secret", credential.getClientSecretEncrypted());
        map.add("code", code);
        map.add("redirect_uri", redirectUri);

        MercadoLivreTokenResponseDTO response = mercadoLivreRestClient
                .post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(map)
                .retrieve()
                .body(MercadoLivreTokenResponseDTO.class);

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(response.expiresIn());
        credentialService.updateToken(salesChannelId,
                new IntegrationCredentialTokenUpdateDTO(response.accessToken(), response.refreshToken(), expiresAt));

        return ResponseEntity.ok("Successfully authorized! you may close this tab.");
    }
}
