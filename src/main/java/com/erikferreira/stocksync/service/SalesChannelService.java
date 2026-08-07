package com.erikferreira.stocksync.service;


import com.erikferreira.stocksync.dto.salesChannel.SalesChannelResponseDTO;
import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.repository.SalesChannelRepository;
import com.erikferreira.stocksync.service.exceptions.DatabaseException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class SalesChannelService {

    private final SalesChannelRepository repository;


    @Transactional(readOnly = true)
    public Page<SalesChannelResponseDTO> findAllPaged(Pageable pageable) {
        Page<SalesChannel> list = repository.findAll(pageable);

        return list.map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public SalesChannelResponseDTO findById(Long id) {
        SalesChannel entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesChannel not found with id " + id));

        return toResponseDTO(entity);
    }

    @Transactional(readOnly = true)
    public SalesChannel getSalesChannelEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesChannel not found with id " + id));
    }

    @Transactional
    public SalesChannelResponseDTO insert(@Valid SalesChannelRequestDTO dto) {
        if (repository.existsByName(dto.name())) {
            throw new DatabaseException("SalesChannel with name '" + dto.name() + "' already exists");
        }

        SalesChannel entity = new SalesChannel();
        copySalesChannelDtoToEntity(dto, entity);
        entity.setActive(true);
        repository.save(entity);

        return toResponseDTO(entity);
    }

    @Transactional
    public SalesChannelResponseDTO update(Long id, @Valid SalesChannelRequestDTO dto) {
        SalesChannel entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesChannel not found: " + id));

        if (repository.existsByNameAndIdNot(dto.name(), id)) {
            throw new DatabaseException("SalesChannel with name '" + dto.name() + "' already exists");
        }

        copySalesChannelDtoToEntity(dto, entity);
        repository.save(entity);

        return toResponseDTO(entity);
    }

    @Transactional
    public void activate(Long id) {
        setActive(id,true);
    }

    @Transactional
    public void deactivate(Long id) {
        setActive(id, false);
    }

    // helpers

    private void setActive(Long id, boolean active) {
        SalesChannel entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesChannel not found: " + id));
        entity.setActive(active);
        repository.save(entity);
    }

    private void copySalesChannelDtoToEntity(SalesChannelRequestDTO dto, SalesChannel entity) {
        entity.setName(dto.name());
        entity.setType(dto.type());
        entity.setBaseUrl(dto.baseUrl());
    }

    private SalesChannelResponseDTO toResponseDTO(SalesChannel salesChannel) {
       return new SalesChannelResponseDTO(
               salesChannel.getId(),
               salesChannel.getName(),
               salesChannel.getType(),
               salesChannel.getBaseUrl(),
               salesChannel.isActive()
       );
    }
}
