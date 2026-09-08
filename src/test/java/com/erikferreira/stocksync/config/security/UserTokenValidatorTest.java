package com.erikferreira.stocksync.config.security;

import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTokenValidatorTest {

    @Mock private UserRepository repository;
    @InjectMocks private UserTokenValidator validator;

    @Test
    void shouldAcceptCurrentVersionForActiveUser() {
        when(repository.findByUsername("viewer"))
                .thenReturn(Optional.of(User.builder().authVersion(2).build()));

        assertThat(validator.validate(token(2L)).hasErrors()).isFalse();
    }

    @Test
    void shouldRejectRevokedVersion() {
        when(repository.findByUsername("viewer"))
                .thenReturn(Optional.of(User.builder().authVersion(3).build()));

        assertThat(validator.validate(token(2L)).hasErrors()).isTrue();
    }

    @Test
    void shouldRejectInactiveUserEvenWithMatchingVersion() {
        when(repository.findByUsername("viewer"))
                .thenReturn(Optional.of(User.builder().active(false).authVersion(2).build()));

        assertThat(validator.validate(token(2L)).hasErrors()).isTrue();
    }

    @Test
    void shouldRejectDeletedUser() {
        when(repository.findByUsername("viewer")).thenReturn(Optional.empty());

        assertThat(validator.validate(token(2L)).hasErrors()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidVersions")
    void shouldRejectMissingOrMalformedVersion(Object version) {
        assertThat(validator.validate(token(version)).hasErrors()).isTrue();
        verifyNoInteractions(repository);
    }

    static Stream<Object> invalidVersions() {
        return Stream.of(null, "2", 2.5, true);
    }

    private Jwt token(Object version) {
        var builder = Jwt.withTokenValue("test-token").header("alg", "HS256").subject("viewer");
        if (version != null) {
            builder.claim("auth_version", version);
        }
        return builder.build();
    }
}
