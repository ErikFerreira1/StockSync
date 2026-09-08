package com.erikferreira.stocksync.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateState() {
        byte[] arrayByte = new byte[32];
        secureRandom.nextBytes(arrayByte);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(arrayByte);
    }

    public String createState(Long salesChannelId, String browserToken) {
        String valueGenerate = generateState();
        PendingState state = new PendingState(
                salesChannelId,
                browserToken,
                Instant.now().plusSeconds(600));

        states.put(valueGenerate, state);

        return valueGenerate;
    }

    public Long consumeState(String state, String browserToken) {
        if (state == null || state.isBlank() || browserToken == null || browserToken.isBlank()) {
            throw invalidState();
        }

        PendingState pendingState = states.get(state);
        if (pendingState == null) {
            throw invalidState();
        }
        if (!pendingState.expiresAt().isAfter(Instant.now())) {
            states.remove(state, pendingState);
            throw invalidState();
        }
        if (!browserToken.equals(pendingState.browserToken())) {
            throw invalidState();
        }
        if (!states.remove(state, pendingState)) {
            throw invalidState();
        }
        return pendingState.salesChannelId();
    }

    private ResponseStatusException invalidState() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OAuth state");
    }

    private record PendingState(
            Long salesChannelId,
            String browserToken,
            Instant expiresAt
    ){}

    private final Map<String, PendingState> states = new ConcurrentHashMap<>();
}
