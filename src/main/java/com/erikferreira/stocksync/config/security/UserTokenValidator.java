package com.erikferreira.stocksync.config.security;

import com.erikferreira.stocksync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object version = token.getClaims().get("auth_version");
        String username = token.getSubject();
        if (username == null || username.isBlank()
                || !(version instanceof Integer || version instanceof Long)) {
            return invalidToken();
        }

        return userRepository.findByUsername(username)
                .filter(user -> user.isActive()
                        && user.getAuthVersion().longValue() == ((Number) version).longValue())
                .map(user -> OAuth2TokenValidatorResult.success())
                .orElseGet(this::invalidToken);
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token is no longer valid", null));
    }
}
