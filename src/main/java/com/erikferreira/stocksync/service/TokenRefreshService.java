package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.dto.integrationCredential.MercadoLivreTokenResponseDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.service.exceptions.CredentialNotAuthorizedException;
import com.erikferreira.stocksync.service.exceptions.InvalidOrderStatusException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final IntegrationCredentialService credentialService;
    private final RestClient mercadoLivreRestClient;

    @Transactional
    public String getValidAccessToken(Long salesChannelId) {
        IntegrationCredential entity = credentialService.getCredentialEntityBySalesChannelId(salesChannelId);

        if (entity.getExpiresAt() == null) {
            throw new CredentialNotAuthorizedException(
                    "Credential for salesChannelId " + salesChannelId + " has not completed OAuth authorization yet"
            );
        }

        boolean verifyTimeToken = LocalDateTime.now().plusMinutes(1).isAfter(entity.getExpiresAt());

        if (!verifyTimeToken) {
            return entity.getAccessToken();
        }

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "refresh_token");
        map.add("client_id", entity.getClientId());
        map.add("client_secret", entity.getClientSecretEncrypted());
        map.add("refresh_token", entity.getRefreshToken());

        MercadoLivreTokenResponseDTO response = mercadoLivreRestClient
                .post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(map)
                .retrieve()
                .body(MercadoLivreTokenResponseDTO.class);


        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(response.expiresIn());

        IntegrationCredentialTokenUpdateDTO credentialTokenDTO =
                new IntegrationCredentialTokenUpdateDTO(
                response.accessToken(),
                response.refreshToken(),
                expiresAt);

        credentialService.updateToken(salesChannelId, credentialTokenDTO);

        return response.accessToken();
    }

}
