package com.erikferreira.stocksync.config.security;

import com.erikferreira.stocksync.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class AuthenticatedUser extends org.springframework.security.core.userdetails.User {

    private final Integer authVersion;

    public AuthenticatedUser(User user) {
        super(user.getUsername(), user.getPasswordHash(), user.isActive(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.authVersion = user.getAuthVersion();
    }

    public Integer getAuthVersion() {
        return authVersion;
    }
}
