package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialInsertDTO;
import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialResponseDTO;
import com.erikferreira.stocksync.dto.integrationCredential.IntegrationCredentialTokenUpdateDTO;
import com.erikferreira.stocksync.entity.IntegrationCredential;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.repository.IntegrationCredentialRepository;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class IntegrationCredentialService {

    private final IntegrationCredentialRepository repository;
    private final SalesChannelService salesChannelService;

    @Transactional(readOnly = true)
    public IntegrationCredentialResponseDTO findById(Long id) {
        return toResponseDTO(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public IntegrationCredential getIntegrationCredentialEntityById(Long id) {
        return findEntityById(id);
    }

    @Transactional(readOnly = true)
    public IntegrationCredential getCredentialEntityBySalesChannelId(Long salesChannelId) {
        return repository.findBySalesChannelId(salesChannelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "IntegrationCredential not found for salesChannelId " + salesChannelId
                ));
    }

    @Transactional(readOnly = true)
    public IntegrationCredentialResponseDTO findBySalesChannelId(Long salesChannelId) {
        return toResponseDTO(getCredentialEntityBySalesChannelId(salesChannelId));
    }

    @Transactional
    public IntegrationCredentialResponseDTO registerCredential(@Valid IntegrationCredentialInsertDTO dto) {
        SalesChannel salesChannel = salesChannelService.getSalesChannelEntityById(dto.salesChannelId());

        IntegrationCredential entity = IntegrationCredential.builder()
                .salesChannel(salesChannel)
                .clientId(dto.clientId())
                .clientSecretEncrypted(dto.clientSecret())
                .build();

        repository.save(entity);
        return toResponseDTO(entity);
    }

    @Transactional
    public IntegrationCredentialResponseDTO updateToken(Long salesChannelId, @Valid IntegrationCredentialTokenUpdateDTO dto) {
        IntegrationCredential entity = getCredentialEntityBySalesChannelId(salesChannelId);

        entity.setAccessToken(dto.accessToken());
        entity.setRefreshToken(dto.refreshToken());
        entity.setExpiresAt(dto.expiresAt());

        repository.save(entity);
        return toResponseDTO(entity);
    }

    // helpers

    private IntegrationCredential findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationCredential not found with id " + id));
    }

    private IntegrationCredentialResponseDTO toResponseDTO(IntegrationCredential entity) {
        return new IntegrationCredentialResponseDTO(
                entity.getId(),
                entity.getSalesChannel().getId(),
                entity.getClientId(),
                entity.getExpiresAt(),
                entity.getAccessToken() != null
        );
    }
}
