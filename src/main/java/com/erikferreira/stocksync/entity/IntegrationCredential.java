package com.erikferreira.stocksync.entity;

import com.erikferreira.stocksync.config.security.CryptoAttributeConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "integration_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "sales_channel_id", nullable = false, unique = true)
    private SalesChannel salesChannel;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Convert(converter = CryptoAttributeConverter.class)
    @Column(name = "client_secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String clientSecretEncrypted;

    @Convert(converter = CryptoAttributeConverter.class)
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Convert(converter = CryptoAttributeConverter.class)
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
