package com.erikferreira.stocksync.integration;

import com.erikferreira.stocksync.StockSyncApplication;
import com.erikferreira.stocksync.entity.enums.UserRole;
import com.erikferreira.stocksync.repository.UserRepository;
import com.erikferreira.stocksync.support.PostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBootstrapIntegrationTest extends PostgreSQLIntegrationTest {

    @Test
    void restartShouldPreserveInitialAdminAndPassword() {
        Long originalId;
        String originalHash;

        try (ConfigurableApplicationContext context = startApplication("bootstrap-admin", "initial-password")) {
            UserRepository repository = context.getBean(UserRepository.class);
            var admin = repository.findByUsername("bootstrap-admin").orElseThrow();

            assertThat(repository.count()).isEqualTo(1);
            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(admin.isActive()).isTrue();
            assertThat(admin.getCreatedAt()).isNotNull();
            assertThat(context.getBean(PasswordEncoder.class)
                    .matches("initial-password", admin.getPasswordHash())).isTrue();

            originalId = admin.getId();
            originalHash = admin.getPasswordHash();
        }

        try (ConfigurableApplicationContext context = startApplication("replacement-admin", "replacement-password")) {
            UserRepository repository = context.getBean(UserRepository.class);
            var admin = repository.findByUsername("bootstrap-admin").orElseThrow();

            assertThat(repository.count()).isEqualTo(1);
            assertThat(repository.existsByUsername("replacement-admin")).isFalse();
            assertThat(admin.getId()).isEqualTo(originalId);
            assertThat(admin.getPasswordHash()).isEqualTo(originalHash);
            assertThat(context.getBean(PasswordEncoder.class)
                    .matches("initial-password", admin.getPasswordHash())).isTrue();
        }
    }

    private ConfigurableApplicationContext startApplication(String username, String password) {
        return new SpringApplicationBuilder(StockSyncApplication.class)
                .profiles("test")
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + POSTGRESQL.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRESQL.getUsername(),
                        "--spring.datasource.password=" + POSTGRESQL.getPassword(),
                        "--app.bootstrap.admin.enabled=true",
                        "--app.bootstrap.admin.username=" + username,
                        "--app.bootstrap.admin.password=" + password);
    }
}
