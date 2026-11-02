package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.dto.integrationCredential.MercadoLivreAuthorizationResponseDTO;
import com.erikferreira.stocksync.dto.integrationCredential.MercadoLivreTokenResponseDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.service.IntegrationCredentialService;
import com.erikferreira.stocksync.service.OAuthStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.net.URI;

@RestController
@Profile("!demo")
@RequiredArgsConstructor
public class MercadoLivreAuthController {

    private final IntegrationCredentialService credentialService;
    private final RestClient mercadoLivreRestClient;
    private final OAuthStateService stateService;

    @Value("${app.mercadolivre.redirect-uri}")
    private String redirectUri;

    @Value("${app.mercadolivre.oauth-cookie-secure:true}")
    private boolean oauthCookieSecure;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping("/mercadolivre/authorize/{salesChannelId}")
    public ResponseEntity<Void> authorize(@PathVariable Long salesChannelId) {
        AuthorizationStart start = createAuthorizationStart(salesChannelId);
        return ResponseEntity.status(302).location(start.authorizationUri())
                .header(HttpHeaders.SET_COOKIE, start.cookie().toString()).build();
    }

    @GetMapping("/mercadolivre/authorization-url/{salesChannelId}")
    public ResponseEntity<MercadoLivreAuthorizationResponseDTO> authorizationUrl(@PathVariable Long salesChannelId) {
        AuthorizationStart start = createAuthorizationStart(salesChannelId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, start.cookie().toString())
                .body(new MercadoLivreAuthorizationResponseDTO(start.authorizationUri().toString()));
    }

    private AuthorizationStart createAuthorizationStart(Long salesChannelId) {
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
        return new AuthorizationStart(url, cookie);
    }

    @GetMapping("/mercadolivre/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = "ml_oauth_browser", required = false) String browserToken) {
        Long salesChannelId = stateService.consumeState(state, browserToken);
        if (error != null && !error.isBlank()) {
            return frontendResult(salesChannelId, "denied");
        }
        if (code == null || code.isBlank()) {
            return frontendResult(salesChannelId, "error");
        }
        IntegrationCredential credential = credentialService.getCredentialEntityBySalesChannelId(salesChannelId);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("client_id", credential.getClientId());
        map.add("client_secret", credential.getClientSecretEncrypted());
        map.add("code", code);
        map.add("redirect_uri", redirectUri);

        try {
            MercadoLivreTokenResponseDTO response = mercadoLivreRestClient
                    .post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(map)
                    .retrieve()
                    .body(MercadoLivreTokenResponseDTO.class);

            if (response == null || response.accessToken() == null || response.refreshToken() == null || response.expiresIn() == null) {
                return frontendResult(salesChannelId, "error");
            }
            Instant expiresAt = Instant.now().plusSeconds(response.expiresIn());
            credentialService.updateToken(salesChannelId,
                    new IntegrationCredentialTokenUpdateDTO(response.accessToken(), response.refreshToken(), expiresAt));
        } catch (RestClientException exception) {
            return frontendResult(salesChannelId, "error");
        }

        return frontendResult(salesChannelId, "connected");
    }

    private ResponseEntity<Void> frontendResult(Long salesChannelId, String result) {
        URI location = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/channels")
                .queryParam("mercadolivre", result)
                .queryParam("channelId", salesChannelId)
                .build().encode().toUri();
        return ResponseEntity.status(302).location(location)
                .header(HttpHeaders.SET_COOKIE, browserCookie("", 0).toString()).build();
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

    private record AuthorizationStart(URI authorizationUri, ResponseCookie cookie) {}
}
