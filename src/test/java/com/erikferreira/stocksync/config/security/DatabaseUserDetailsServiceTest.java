package com.erikferreira.stocksync.config.security;

import com.erikferreira.stocksync.entity.User;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DatabaseUserDetailsService userDetailsService;

    @Test
    void loadUserByUsernameShouldMapActiveUser() {
        User user = createUser(true, UserRole.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        var result = userDetailsService.loadUserByUsername("admin");

        assertThat(result.getUsername()).isEqualTo("admin");
        assertThat(result.getPassword()).isEqualTo("{bcrypt}encoded-password");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsernameShouldDisableInactiveUser() {
        User user = createUser(false, UserRole.VIEWER);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        var result = userDetailsService.loadUserByUsername("admin");

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_VIEWER");
    }

    @Test
    void loadUserByUsernameShouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");
    }

    private User createUser(boolean active, UserRole role) {
        return User.builder()
                .id(1L)
                .username("admin")
                .passwordHash("{bcrypt}encoded-password")
                .role(role)
                .active(active)
                .build();
    }
}
