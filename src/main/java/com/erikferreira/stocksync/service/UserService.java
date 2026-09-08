package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.user.PasswordChangeDTO;
import com.erikferreira.stocksync.dto.user.UserInsertDTO;
import com.erikferreira.stocksync.dto.user.UserResponseDTO;
import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.service.exceptions.DatabaseException;
import com.erikferreira.stocksync.service.exceptions.InvalidCurrentPasswordException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import com.erikferreira.stocksync.service.exceptions.UsernameAlreadyExistsException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> findAllPaged(Pageable pageable) {
       Page<User> list = userRepository.findAll(pageable);

       return list.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        return toResponse(user);
    }

    @Transactional
    public UserResponseDTO insert(@Valid UserInsertDTO insertDTO) {
        if (userRepository.existsByUsername(insertDTO.username())) {
            throw new UsernameAlreadyExistsException(
                    "Username already exists: " + insertDTO.username());
        }

        if (insertDTO.role() == UserRole.ADMIN && userRepository.existsByRole(UserRole.ADMIN)) {
            throw new DatabaseException("An administrator already exists");
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

    @Transactional
    public void activate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setActive(true);
    }

    @Transactional
    public void deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (user.getRole() == UserRole.ADMIN) {
            throw new DatabaseException("Cannot deactivate the administrator");
        }

        user.setActive(false);
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
