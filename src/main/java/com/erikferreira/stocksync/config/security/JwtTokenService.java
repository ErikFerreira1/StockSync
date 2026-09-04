package com.erikferreira.stocksync.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JwtTokenService {

    public static final String ISSUER = "https://stocksync.local";

    private final JwtEncoder jwtEncoder;
    private final Duration expiration;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${app.security.jwt.expiration}") Duration expiration) {
        this.jwtEncoder = jwtEncoder;
        this.expiration = expiration;
    }

    public Jwt generate(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("It is not possible to generate a token for a null user");
        }
        if (!authentication.isAuthenticated()) {
            throw new IllegalArgumentException("It is not possible to generate a token for an unauthenticated user");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiration);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        JwtEncoderParameters jwtEncoderParameters = JwtEncoderParameters.from(jwsHeader, jwtClaimsSet);

        return jwtEncoder.encode(jwtEncoderParameters);
    }
}
