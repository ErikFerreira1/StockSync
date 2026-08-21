package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.salesChannel.SalesChannelRequestDTO;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.enums.ChannelType;
import com.erikferreira.stocksync.repository.SalesChannelRepository;
import com.erikferreira.stocksync.service.exceptions.DatabaseException;
import com.erikferreira.stocksync.service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesChannelServiceTest {

    @Mock
    private SalesChannelRepository repository;
    @InjectMocks
    private SalesChannelService service;

    private SalesChannel channel;
    private SalesChannelRequestDTO request;

    @BeforeEach
    void setUp() {
        channel = SalesChannel.builder()
                .id(1L).name("Mercado Livre").type(ChannelType.MERCADO_LIVRE)
                .baseUrl("https://api.mercadolibre.com").active(true).build();
        request = new SalesChannelRequestDTO(
                "Mercado Livre", ChannelType.MERCADO_LIVRE, "https://api.mercadolibre.com");
    }

    @Test
    void findAllPagedShouldMapChannels() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(channel), pageable, 1));

        var result = service.findAllPaged(pageable);

        assertThat(result.getContent()).singleElement()
                .satisfies(dto -> assertThat(dto.id()).isEqualTo(1L));
    }

    @Test
    void findByIdShouldReturnChannel() {
        when(repository.findById(1L)).thenReturn(Optional.of(channel));
        assertThat(service.findById(1L).name()).isEqualTo("Mercado Livre");
    }

    @Test
    void getEntityShouldThrowWhenChannelDoesNotExist() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSalesChannelEntityById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void insertShouldSaveActiveChannel() {
        when(repository.existsByName(request.name())).thenReturn(false);
        when(repository.save(any(SalesChannel.class))).thenAnswer(invocation -> {
            SalesChannel saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var result = service.insert(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.active()).isTrue();
        verify(repository).save(any(SalesChannel.class));
    }

    @Test
    void insertShouldRejectDuplicateName() {
        when(repository.existsByName(request.name())).thenReturn(true);
        assertThatThrownBy(() -> service.insert(request)).isInstanceOf(DatabaseException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateShouldChangeChannel() {
        var update = new SalesChannelRequestDTO("ML Principal", ChannelType.MERCADO_LIVRE, "https://example.com");
        when(repository.findById(1L)).thenReturn(Optional.of(channel));
        when(repository.existsByNameAndIdNot(update.name(), 1L)).thenReturn(false);
        when(repository.save(channel)).thenReturn(channel);

        var result = service.update(1L, update);

        assertThat(result.name()).isEqualTo("ML Principal");
        assertThat(result.baseUrl()).isEqualTo("https://example.com");
    }

    @Test
    void updateShouldRejectNameUsedByAnotherChannel() {
        when(repository.findById(1L)).thenReturn(Optional.of(channel));
        when(repository.existsByNameAndIdNot(request.name(), 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, request)).isInstanceOf(DatabaseException.class);
    }

    @Test
    void activateAndDeactivateShouldChangeStatus() {
        when(repository.findById(1L)).thenReturn(Optional.of(channel));

        service.deactivate(1L);
        assertThat(channel.isActive()).isFalse();

        service.activate(1L);
        assertThat(channel.isActive()).isTrue();
        verify(repository, org.mockito.Mockito.times(2)).save(channel);
    }
}
