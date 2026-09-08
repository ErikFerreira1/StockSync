package com.erikferreira.stocksync.config.security;

import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.entity.enums.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final Duration EXPIRATION = Duration.ofHours(8);

    @Mock
    private JwtEncoder jwtEncoder;

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(jwtEncoder, EXPIRATION);
    }

    @Test
    void generateShouldEncodeTokenWithExpectedHeaderAndClaims() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(User.builder().username("admin").passwordHash("hash")
                        .role(UserRole.ADMIN).authVersion(3).build()),
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        Jwt encodedJwt = mock(Jwt.class);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt);
        Instant beforeGeneration = Instant.now();

        Jwt result = jwtTokenService.generate(authentication);

        Instant afterGeneration = Instant.now();
        ArgumentCaptor<JwtEncoderParameters> parametersCaptor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parametersCaptor.capture());

        JwtEncoderParameters parameters = parametersCaptor.getValue();
        assertThat(result).isSameAs(encodedJwt);
        assertThat(parameters.getJwsHeader().getAlgorithm().getName()).isEqualTo("HS256");
        assertThat(parameters.getClaims().getIssuer()).hasToString("https://stocksync.local");
        assertThat(parameters.getClaims().getSubject()).isEqualTo("admin");
        assertThat(parameters.getClaims().getIssuedAt()).isBetween(beforeGeneration, afterGeneration);
        assertThat(parameters.getClaims().getExpiresAt())
                .isEqualTo(parameters.getClaims().getIssuedAt().plus(EXPIRATION));
        assertThat(parameters.getClaims().getClaims().get("roles"))
                .isEqualTo(List.of("ROLE_ADMIN"));
        assertThat(parameters.getClaims().getClaims().get("auth_version")).isEqualTo(3);
    }

    @Test
    void generateShouldRejectNullAuthentication() {
        assertThatThrownBy(() -> jwtTokenService.generate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("It is not possible to generate a token for a null user");

        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void generateShouldRejectUnauthenticatedUser() {
        Authentication authentication = UsernamePasswordAuthenticationToken.unauthenticated(
                "admin",
                "password"
        );

        assertThatThrownBy(() -> jwtTokenService.generate(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("It is not possible to generate a token for an unauthenticated user");

        verifyNoInteractions(jwtEncoder);
    }
}
