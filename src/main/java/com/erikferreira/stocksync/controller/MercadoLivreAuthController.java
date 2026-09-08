package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.dto.integrationCredential.MercadoLivreTokenResponseDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.service.IntegrationCredentialService;
import com.erikferreira.stocksync.service.OAuthStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class MercadoLivreAuthController {

    private final IntegrationCredentialService credentialService;
    private final RestClient mercadoLivreRestClient;
    private final OAuthStateService stateService;

    @Value("${app.mercadolivre.redirect-uri}")
    private String redirectUri;

    @Value("${app.mercadolivre.oauth-cookie-secure:true}")
    private boolean oauthCookieSecure;

    @GetMapping("/mercadolivre/authorize/{salesChannelId}")
    public ResponseEntity<Void> authorize(@PathVariable Long salesChannelId) {
        IntegrationCredential credential = credentialService.getCredentialEntityBySalesChannelId(salesChannelId);
        String browserToken = stateService.generateState();
        String state = stateService.createState(salesChannelId, browserToken);

        var url = UriComponentsBuilder.fromUriString("https://auth.mercadolivre.com.br/authorization")
                .queryParam("response_type", "code")
                .queryParam("client_id", credential.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build().encode().toUri();

        ResponseCookie cookie = browserCookie(browserToken, 600);

        return ResponseEntity.status(302).location(url)
                .header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @GetMapping("/mercadolivre/callback")
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam String state,
            @CookieValue(name = "ml_oauth_browser", required = false) String browserToken) {
        Long salesChannelId = stateService.consumeState(state, browserToken);
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

        Instant expiresAt = Instant.now().plusSeconds(response.expiresIn());
        credentialService.updateToken(salesChannelId,
                new IntegrationCredentialTokenUpdateDTO(response.accessToken(), response.refreshToken(), expiresAt));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, browserCookie("", 0).toString())
                .body("Successfully authorized! you may close this tab.");
    }

    private ResponseCookie browserCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from("ml_oauth_browser", value)
                .httpOnly(true)
                .secure(oauthCookieSecure)
                .sameSite("Lax")
                .path("/mercadolivre")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
