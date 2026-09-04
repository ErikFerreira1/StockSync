package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.user.PasswordChangeDTO;
import com.erikferreira.stocksync.dto.user.UserInsertDTO;
import com.erikferreira.stocksync.dto.user.UserResponseDTO;
import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidCurrentPasswordException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import com.erikferreira.stocksync.service.exceptions.UsernameAlreadyExistsException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    @Transactional
    public UserResponseDTO insert(@Valid UserInsertDTO insertDTO) {
        if (userRepository.existsByUsername(insertDTO.username())) {
            throw new UsernameAlreadyExistsException(
                    "Username already exists: " + insertDTO.username());
        }

        String passwordHash = passwordEncoder.encode(insertDTO.password());

        User user = User.builder()
                .username(insertDTO.username())
                .passwordHash(passwordHash)
                .role(insertDTO.role())
                .active(true)
                .build();

        userRepository.save(user);

        return toResponse(user);
    }

    @Transactional
    public void changePassword(String username, @Valid PasswordChangeDTO passwordChangeDTO) {
       User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Username not found " + username));

        if (!passwordEncoder.matches(passwordChangeDTO.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(passwordChangeDTO.newPassword()));
    }

    // helpers

    private UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }

}
