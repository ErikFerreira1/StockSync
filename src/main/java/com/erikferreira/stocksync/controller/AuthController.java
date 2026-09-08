package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.auth.LoginRequestDTO;
import com.erikferreira.stocksync.dto.auth.LoginResponseDTO;
import com.erikferreira.stocksync.service.AuthService;
import com.erikferreira.stocksync.service.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO,
                                                HttpServletRequest request) {
        long retryAfter = loginRateLimiter.tryAcquire(request.getRemoteAddr());
        if (retryAfter > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter)).build();
        }
        return ResponseEntity.ok(authService.login(loginRequestDTO));
    }
}
