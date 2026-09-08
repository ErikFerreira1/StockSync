package com.erikferreira.stocksync.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class OAuthStateServiceTest {

    private final OAuthStateService service = new OAuthStateService();

    @Test
    void shouldReturnChannelAndRejectReuse() {
        String state = service.createState(7L, "browser");

        assertThat(service.consumeState(state, "browser")).isEqualTo(7L);
        assertInvalid(state, "browser");
    }

    @Test
    void shouldRejectAnotherBrowserWithoutConsumingState() {
        String state = service.createState(7L, "browser");

        assertInvalid(state, "other-browser");
        assertThat(service.consumeState(state, "browser")).isEqualTo(7L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown-state"})
    void shouldRejectInvalidState(String state) {
        assertInvalid(state, "browser");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void shouldRejectMissingBrowserToken(String browserToken) {
        String state = service.createState(7L, "browser");

        assertInvalid(state, browserToken);
        assertThat(service.consumeState(state, "browser")).isEqualTo(7L);
    }

    @Test
    void shouldRejectStateAtExpiration() {
        Instant start = Instant.now();
        Instant expiration = start.plusSeconds(600);
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(start);
            String state = service.createState(7L, "browser");
            clock.when(Instant::now).thenReturn(expiration);

            assertInvalid(state, "browser");
        }
    }

    @Test
    void concurrentRequestsShouldConsumeStateOnlyOnce() throws Exception {
        String state = service.createState(7L, "browser");
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> consume = () -> {
            start.await();
            try {
                service.consumeState(state, "browser");
                return true;
            } catch (ResponseStatusException exception) {
                return false;
            }
        };
        try {
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            start.countDown();

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertInvalid(String state, String browserToken) {
        assertThatThrownBy(() -> service.consumeState(state, browserToken))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("Invalid or expired OAuth state");
                });
    }
}
