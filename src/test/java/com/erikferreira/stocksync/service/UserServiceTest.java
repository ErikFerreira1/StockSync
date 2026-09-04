package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.user.PasswordChangeDTO;
import com.erikferreira.stocksync.dto.user.UserInsertDTO;
import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidCurrentPasswordException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import com.erikferreira.stocksync.service.exceptions.UsernameAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserInsertDTO insertDTO;
    private String passwordHash;

    @BeforeEach
    void setUp() {
        insertDTO = new UserInsertDTO(
                "admin",
                "password123",
                UserRole.ADMIN
        );
        passwordHash = "{bcrypt}encoded-password";
    }

    @Test
    void insertShouldEncodePasswordSaveUserAndReturnResponse() {
        Instant createdAt = Instant.parse("2026-08-31T12:00:00Z");
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn(passwordHash);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(1L);
            savedUser.setCreatedAt(createdAt);
            return savedUser;
        });

        var response = userService.insert(insertDTO);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(createdAt);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash())
                .isEqualTo(passwordHash)
                .isNotEqualTo(insertDTO.password());
        verify(passwordEncoder).encode(insertDTO.password());
    }

    @Test
    void insertShouldThrowWhenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> userService.insert(insertDTO))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessageContaining("admin");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordShouldReplaceHashWhenCurrentPasswordIsCorrect() {
        User user = User.builder()
                .username("admin")
                .passwordHash("{bcrypt}current-hash")
                .role(UserRole.ADMIN)
                .build();
        PasswordChangeDTO changeDTO = new PasswordChangeDTO("current-password", "new-password");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "{bcrypt}current-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("{bcrypt}new-hash");

        userService.changePassword("admin", changeDTO);

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new-hash");
        verify(passwordEncoder).encode("new-password");
    }

    @Test
    void changePasswordShouldRejectIncorrectCurrentPassword() {
        User user = User.builder()
                .username("admin")
                .passwordHash("{bcrypt}current-hash")
                .role(UserRole.ADMIN)
                .build();
        PasswordChangeDTO changeDTO = new PasswordChangeDTO("wrong-password", "new-password");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "{bcrypt}current-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword("admin", changeDTO))
                .isInstanceOf(InvalidCurrentPasswordException.class)
                .hasMessage("Current password is incorrect");

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}current-hash");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void changePasswordShouldRejectUnknownUser() {
        PasswordChangeDTO changeDTO = new PasswordChangeDTO("current-password", "new-password");
        when(userRepository.findByUsername("missing-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword("missing-user", changeDTO))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing-user");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
