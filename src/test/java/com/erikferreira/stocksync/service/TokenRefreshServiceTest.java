package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.service.exceptions.CredentialNotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class TokenRefreshServiceTest {

    @Mock
    private IntegrationCredentialService credentialService;

    private TokenRefreshService service;
    private MockRestServiceServer server;
    private IntegrationCredential credential;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.mercadolibre.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new TokenRefreshService(credentialService, builder.build());
        credential = IntegrationCredential.builder()
                .clientId("client-id")
                .clientSecretEncrypted("client-secret")
                .accessToken("current-access")
                .refreshToken("current-refresh")
                .build();
    }

    @Test
    void getValidAccessTokenShouldRejectCredentialWithoutAuthorization() {
        when(credentialService.getCredentialEntityBySalesChannelId(1L)).thenReturn(credential);

        assertThatThrownBy(() -> service.getValidAccessToken(1L))
                .isInstanceOf(CredentialNotAuthorizedException.class);

        verify(credentialService, never()).updateToken(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getValidAccessTokenShouldReturnCurrentTokenWhenNotNearExpiration() {
        credential.setExpiresAt(Instant.now().plusSeconds(3600));
        when(credentialService.getCredentialEntityBySalesChannelId(1L)).thenReturn(credential);

        assertThat(service.getValidAccessToken(1L)).isEqualTo("current-access");

        verify(credentialService, never()).updateToken(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        server.verify();
    }

    @Test
    void getValidAccessTokenShouldRefreshAndPersistExpiredToken() {
        credential.setExpiresAt(Instant.now().minusSeconds(1));
        when(credentialService.getCredentialEntityBySalesChannelId(1L)).thenReturn(credential);
        server.expect(requestTo("https://api.mercadolibre.com/oauth/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\",\"expires_in\":21600}",
                        MediaType.APPLICATION_JSON));
        Instant before = Instant.now().plusSeconds(21599);

        String result = service.getValidAccessToken(1L);

        Instant after = Instant.now().plusSeconds(21601);
        assertThat(result).isEqualTo("new-access");
        ArgumentCaptor<IntegrationCredentialTokenUpdateDTO> captor =
                ArgumentCaptor.forClass(IntegrationCredentialTokenUpdateDTO.class);
        verify(credentialService).updateToken(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(captor.getValue().accessToken()).isEqualTo("new-access");
        assertThat(captor.getValue().refreshToken()).isEqualTo("new-refresh");
        assertThat(captor.getValue().expiresAt()).isBetween(before, after);
        server.verify();
    }
}
