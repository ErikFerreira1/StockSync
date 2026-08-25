package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialInsertDTO;
import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.repository.IntegrationCredentialRepository;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationCredentialServiceTest {

    @Mock
    private IntegrationCredentialRepository repository;
    @Mock
    private SalesChannelService salesChannelService;
    @InjectMocks
    private IntegrationCredentialService service;

    private SalesChannel channel;
    private IntegrationCredential credential;

    @BeforeEach
    void setUp() {
        channel = SalesChannel.builder().id(1L).name("ML").type(ChannelType.MERCADO_LIVRE).active(true).build();
        credential = IntegrationCredential.builder()
                .id(2L).salesChannel(channel).clientId("client").clientSecretEncrypted("secret").build();
    }

    @Test
    void findByIdShouldHideSecretsAndReportDisconnectedCredential() {
        when(repository.findById(2L)).thenReturn(Optional.of(credential));

        var result = service.findById(2L);

        assertThat(result.clientId()).isEqualTo("client");
        assertThat(result.connected()).isFalse();
    }

    @Test
    void getBySalesChannelShouldReturnEntity() {
        when(repository.findBySalesChannelId(1L)).thenReturn(Optional.of(credential));
        assertThat(service.getCredentialEntityBySalesChannelId(1L)).isSameAs(credential);
    }

    @Test
    void getBySalesChannelShouldThrowWhenMissing() {
        when(repository.findBySalesChannelId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCredentialEntityBySalesChannelId(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registerCredentialShouldResolveChannelAndSaveCredential() {
        var dto = new IntegrationCredentialInsertDTO(1L, "client", "secret");
        when(salesChannelService.getSalesChannelEntityById(1L)).thenReturn(channel);
        when(repository.save(any(IntegrationCredential.class))).thenAnswer(invocation -> {
            IntegrationCredential saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        var result = service.registerCredential(dto);

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.salesChannelId()).isEqualTo(1L);
        verify(repository).save(any(IntegrationCredential.class));
    }

    @Test
    void updateTokenShouldUpdateAllTokenFields() {
        Instant expiration = Instant.now().plusSeconds(21600);
        var dto = new IntegrationCredentialTokenUpdateDTO("access", "refresh", expiration);
        when(repository.findBySalesChannelId(1L)).thenReturn(Optional.of(credential));

        var result = service.updateToken(1L, dto);

        assertThat(credential.getAccessToken()).isEqualTo("access");
        assertThat(credential.getRefreshToken()).isEqualTo("refresh");
        assertThat(credential.getExpiresAt()).isEqualTo(expiration);
        assertThat(result.connected()).isTrue();
        verify(repository).save(credential);
    }
}
