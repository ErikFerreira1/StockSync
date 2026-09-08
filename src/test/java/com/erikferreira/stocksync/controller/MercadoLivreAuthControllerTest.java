package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.controller.handler.GlobalExceptionHandler;
import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.service.IntegrationCredentialService;
import com.erikferreira.stocksync.service.OAuthStateService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MercadoLivreAuthControllerTest {

    private final IntegrationCredentialService credentials = mock(IntegrationCredentialService.class);
    private final OAuthStateService states = new OAuthStateService();
    private MercadoLivreAuthController controller;
    private MockMvc mvc;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.mercadolibre.com");
        server = MockRestServiceServer.bindTo(builder).build();
        controller = new MercadoLivreAuthController(credentials, builder.build(), states);
        ReflectionTestUtils.setField(controller, "redirectUri", "https://stocksync.test/mercadolivre/callback");
        ReflectionTestUtils.setField(controller, "oauthCookieSecure", true);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void authorizeShouldBindRandomStateToSecureBrowserCookie() throws Exception {
        Authorization authorization = authorize();

        assertThat(authorization.state()).matches("[A-Za-z0-9_-]{43}");
        assertThat(authorization.cookie().getValue()).matches("[A-Za-z0-9_-]{43}")
                .isNotEqualTo(authorization.state());
        assertThat(authorization.cookie().isHttpOnly()).isTrue();
        assertThat(authorization.cookie().getSecure()).isTrue();
        assertThat(authorization.cookie().getSameSite()).isEqualTo("Lax");
        assertThat(authorization.cookie().getPath()).isEqualTo("/mercadolivre");
        assertThat(authorization.cookie().getMaxAge()).isEqualTo(600);
        assertThat(states.consumeState(authorization.state(), authorization.cookie().getValue())).isEqualTo(7L);
    }

    @Test
    void cookieSecurityShouldBeConfigurableForLocalHttp() throws Exception {
        ReflectionTestUtils.setField(controller, "oauthCookieSecure", false);

        assertThat(authorize().cookie().getSecure()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-cookie", "wrong-browser", "channel-id", "expired"})
    void invalidCallbackShouldNotAccessCredentialsOrExchangeTokens(String scenario) throws Exception {
        String state = states.createState(7L, "browser");
        Cookie cookie = new Cookie("ml_oauth_browser", "browser");
        if (scenario.equals("channel-id")) {
            state = "7";
        } else if (scenario.equals("wrong-browser")) {
            cookie.setValue("another-browser");
        }
        var request = get("/mercadolivre/callback").param("code", "oauth-code").param("state", state);
        if (!scenario.equals("missing-cookie")) {
            request.cookie(cookie);
        }
        if (scenario.equals("expired")) {
            Instant future = Instant.now().plusSeconds(601);
            try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
                clock.when(Instant::now).thenReturn(future);
                mvc.perform(request).andExpect(status().isBadRequest());
            }
        } else {
            mvc.perform(request).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(credentials);
        server.verify();
    }

    @Test
    void validCallbackShouldUpdateOriginalChannelDeleteCookieAndRejectReplay() throws Exception {
        Authorization authorization = authorize();
        clearInvocations(credentials);
        server.expect(requestTo("https://api.mercadolibre.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"access_token":"test-access","refresh_token":"test-refresh","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));

        var request = get("/mercadolivre/callback").param("code", "oauth-code")
                .param("state", authorization.state()).cookie(authorization.cookie());
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();

        var updated = ArgumentCaptor.forClass(IntegrationCredentialTokenUpdateDTO.class);
        verify(credentials).getCredentialEntityBySalesChannelId(7L);
        verify(credentials).updateToken(eq(7L), updated.capture());
        assertThat(updated.getValue().accessToken()).isEqualTo("test-access");
        assertThat(updated.getValue().refreshToken()).isEqualTo("test-refresh");
        MockCookie deleted = MockCookie.parse(response.getHeader(HttpHeaders.SET_COOKIE));
        assertThat(deleted.getName()).isEqualTo("ml_oauth_browser");
        assertThat(deleted.getPath()).isEqualTo("/mercadolivre");
        assertThat(deleted.getMaxAge()).isZero();

        clearInvocations(credentials);
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(credentials);
        server.verify();
    }

    private Authorization authorize() throws Exception {
        when(credentials.getCredentialEntityBySalesChannelId(7L)).thenReturn(IntegrationCredential.builder()
                .clientId("client-id").clientSecretEncrypted("test-secret").build());
        var response = mvc.perform(get("/mercadolivre/authorize/7"))
                .andExpect(status().isFound()).andReturn().getResponse();
        String state = UriComponentsBuilder.fromUriString(response.getHeader(HttpHeaders.LOCATION))
                .build().getQueryParams().getFirst("state");
        return new Authorization(state, MockCookie.parse(response.getHeader(HttpHeaders.SET_COOKIE)));
    }

    private record Authorization(String state, MockCookie cookie) {}
}
