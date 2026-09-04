package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.config.security.JwtTokenService;
import com.erikferreira.stocksync.dto.auth.LoginRequestDTO;
import com.erikferreira.stocksync.dto.auth.LoginResponseDTO;
import com.erikferreira.stocksync.dto.user.UserResponseDTO;
import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                new UsernamePasswordAuthenticationToken(
                        loginRequestDTO.username(),
                        loginRequestDTO.password());

        var authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);

        Jwt jwt = jwtTokenService.generate(authentication);

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserResponseDTO userResponseDTO = new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );

        return new LoginResponseDTO(
                jwt.getTokenValue(),
                "Bearer",
                jwt.getExpiresAt(),
                userResponseDTO
        );

    }



}
