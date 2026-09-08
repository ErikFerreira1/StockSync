package com.erikferreira.stocksync.config;

import com.erikferreira.stocksync.dto.user.UserInsertDTO;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
class AdminBootstrap implements ApplicationRunner {

    @Value("${app.bootstrap.admin.username}")
    private String username;

    @Value("${app.bootstrap.admin.password}")
    private String password;

    private final UserRepository userRepository;
    private final UserService userService;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }

        userService.insert(new UserInsertDTO(
                username,
                password,
                UserRole.ADMIN
        ));
    }
}
