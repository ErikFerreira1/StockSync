package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.controller.handler.GlobalExceptionHandler;
import com.erikferreira.stocksync.service.AuthService;
import com.erikferreira.stocksync.service.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    @Test
    void shouldThrottleBeforeAuthenticationEvenWhenUsernameOrForwardedHeaderChanges() throws Exception {
        AuthService service = mock(AuthService.class);
        when(service.login(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        var mvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(service, new LoginRateLimiter(10, Duration.ofMinutes(1))))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        for (int i = 0; i < 11; i++) {
            var response = mvc.perform(post("/auth/login")
                    .header("X-Forwarded-For", "192.0.2." + i)
                    .contentType("application/json")
                    .content("{\"username\":\"user" + i + "\",\"password\":\"wrong-password\"}"));
            response.andExpect(i < 10 ? status().isUnauthorized() : status().isTooManyRequests());
            if (i == 10) {
                response.andExpect(header().exists("Retry-After"));
            }
        }
        verify(service, times(10)).login(any());
        mvc.perform(post("/auth/login").with(request -> {
                    request.setRemoteAddr("192.0.2.100");
                    return request;
                }).contentType("application/json")
                .content("{\"username\":\"user\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        verify(service, times(11)).login(any());
    }
}
