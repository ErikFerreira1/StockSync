package com.erikferreira.stocksync.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginRateLimiterTest {

    @Test
    void shouldLimitClientsIndependentlyAndAllowAttemptsAfterExpiration() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-09-08T12:00:00Z");
        when(clock.instant()).thenReturn(start);
        var limiter = new LoginRateLimiter(2, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("client-a")).isZero();
        assertThat(limiter.tryAcquire("client-a")).isZero();
        assertThat(limiter.tryAcquire("client-a")).isEqualTo(60);
        assertThat(limiter.tryAcquire("client-b")).isZero();

        when(clock.instant()).thenReturn(start.plusMillis(59_500));
        assertThat(limiter.tryAcquire("client-a")).isEqualTo(1);
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThat(limiter.tryAcquire("client-a")).isZero();
    }

    @Test
    void concurrentRequestsShouldNotExceedLimit() throws Exception {
        var limiter = new LoginRateLimiter(10, Duration.ofMinutes(1),
                Clock.fixed(Instant.now(), java.time.ZoneOffset.UTC));
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = IntStream.range(0, 100)
                    .mapToObj(i -> (Callable<Long>) () -> limiter.tryAcquire("client")).toList();
            int permitted = 0;
            for (var result : executor.invokeAll(tasks)) {
                if (result.get() == 0) {
                    permitted++;
                }
            }
            assertThat(permitted).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldBoundTrackedClientsAndReclaimExpiredEntries() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.now();
        when(clock.instant()).thenReturn(start);
        var limiter = new LoginRateLimiter(10, Duration.ofMinutes(1), clock);
        for (int i = 0; i < 10_000; i++) {
            assertThat(limiter.tryAcquire("client-" + i)).isZero();
        }
        assertThat(limiter.tryAcquire("new-client")).isPositive();
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThat(limiter.tryAcquire("new-client")).isZero();
    }
}
