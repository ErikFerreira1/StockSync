package com.erikferreira.stocksync.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class LoginRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Attempts> clients = new HashMap<>();
    private Instant nextCleanup = Instant.MIN;

    @Autowired
    public LoginRateLimiter(
            @Value("${app.security.login.max-attempts:10}") int maxAttempts,
            @Value("${app.security.login.window:PT1M}") Duration window) {
        this(maxAttempts, window, Clock.systemUTC());
    }

    LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1 || window.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("Login limits require positive attempts and a window of at least one second");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    // Zero permits the attempt; a positive value is the Retry-After delay in seconds.
    public synchronized long tryAcquire(String clientAddress) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanup)) {
            clients.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            nextCleanup = now.plus(window);
        }

        Attempts attempts = clients.get(clientAddress);
        if (attempts == null || !attempts.expiresAt().isAfter(now)) {
            if (attempts == null && clients.size() >= MAX_TRACKED_CLIENTS) {
                return retryAfter(now, nextCleanup);
            }
            attempts = new Attempts(0, now.plus(window));
        }
        if (attempts.count() >= maxAttempts) {
            return retryAfter(now, attempts.expiresAt());
        }
        clients.put(clientAddress, new Attempts(attempts.count() + 1, attempts.expiresAt()));
        return 0;
    }

    private long retryAfter(Instant now, Instant expiresAt) {
        return Math.max(1, (Duration.between(now, expiresAt).toMillis() + 999) / 1000);
    }

    private record Attempts(int count, Instant expiresAt) {}
}
