package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest extends PostgreSQLIntegrationTest {

    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger();
    private String clientAddress;

    private static final String ADMIN_USERNAME = "security-admin";
    private static final String ADMIN_PASSWORD = "admin-password";
    private static final String VIEWER_USERNAME = "security-viewer";
    private static final String VIEWER_PASSWORD = "viewer-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUpUsers() {
        clientAddress = "192.0.2." + CLIENT_SEQUENCE.incrementAndGet();
        userRepository.save(User.builder()
                .username(ADMIN_USERNAME)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(UserRole.ADMIN)
                .active(true)
                .build());
        userRepository.saveAndFlush(User.builder()
                .username(VIEWER_USERNAME)
                .passwordHash(passwordEncoder.encode(VIEWER_PASSWORD))
                .role(UserRole.VIEWER)
                .active(true)
                .build());
    }

    @Test
    void loginShouldReturnValidJwtForCorrectCredentials() throws Exception {
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(ADMIN_USERNAME);
        assertThat(jwt.getIssuer()).hasToString("https://stocksync.local");
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("ROLE_ADMIN"));
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void loginShouldRejectInvalidCredentials() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginBody(ADMIN_USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void protectedEndpointShouldRejectRequestWithoutToken() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerShouldReadButNotCreateProducts() throws Exception {
        String token = login(VIEWER_USERNAME, VIEWER_PASSWORD);

        mvc.perform(get("/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(post("/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "sku": "SECURITY-TEST",
                                  "name": "Security test product",
                                  "basePrice": 10.00,
                                  "initialQuantity": 1,
                                  "minQuantity": 0
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldCreateUsers() throws Exception {
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mvc.perform(post("/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "created-by-admin",
                                  "password": "password123",
                                  "role": "OPERATOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("created-by-admin"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void authenticatedUserShouldChangeOwnPassword() throws Exception {
        String token = login(VIEWER_USERNAME, VIEWER_PASSWORD);

        mvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(passwordChangeBody(VIEWER_PASSWORD, "new-viewer-password")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        String newToken = login(VIEWER_USERNAME, "new-viewer-password");
        mvc.perform(get("/products").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content(loginBody(VIEWER_USERNAME, VIEWER_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivationShouldRevokeTokensEvenAfterReactivation() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String viewerToken = login(VIEWER_USERNAME, VIEWER_PASSWORD);
        Long viewerId = userRepository.findByUsername(VIEWER_USERNAME).orElseThrow().getId();

        mvc.perform(patch("/users/{id}/deactivate", viewerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/products").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content(loginBody(VIEWER_USERNAME, VIEWER_PASSWORD)))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/users/{id}/activate", viewerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/products").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isUnauthorized());

        String newToken = login(VIEWER_USERNAME, VIEWER_PASSWORD);
        mvc.perform(get("/products").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordShouldRejectIncorrectCurrentPassword() throws Exception {
        String token = login(VIEWER_USERNAME, VIEWER_PASSWORD);

        mvc.perform(patch("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(passwordChangeBody("wrong-password", "new-viewer-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid current password"))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changePasswordShouldRequireAuthentication() throws Exception {
        mvc.perform(patch("/users/me/password")
                        .contentType("application/json")
                        .content(passwordChangeBody(VIEWER_PASSWORD, "new-viewer-password")))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(clientAddress);
                            return request;
                        })
                        .contentType("application/json")
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseBody = objectMapper.readTree(response);
        return responseBody.get("accessToken").asText();
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginCredentials(username, password));
    }

    private String passwordChangeBody(String currentPassword, String newPassword) throws Exception {
        return objectMapper.writeValueAsString(new PasswordChangeCredentials(currentPassword, newPassword));
    }

    private record LoginCredentials(String username, String password) {
    }

    private record PasswordChangeCredentials(String currentPassword, String newPassword) {
    }
}
