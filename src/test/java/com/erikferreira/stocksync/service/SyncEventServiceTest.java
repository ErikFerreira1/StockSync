package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.syncEvent.SyncEventInsertDTO;
import com.erikferreira.stocksync.entity.Order;
import com.erikferreira.stocksync.entity.Product;
import com.erikferreira.stocksync.entity.SalesChannel;
import com.erikferreira.stocksync.entity.SyncEvent;
import com.erikferreira.stocksync.entity.enums.SyncStatus;
import com.erikferreira.stocksync.factory.ProductFactory;
import com.erikferreira.stocksync.repository.SyncEventRepository;
import com.erikferreira.stocksync.service.exceptions.InvalidSyncEventException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncEventServiceTest {

    @Mock private SyncEventRepository repository;
    @Mock private ProductService productService;
    @Mock private SalesChannelService salesChannelService;
    @Mock private OrderService orderService;
    @InjectMocks private SyncEventService service;

    private Product product;
    private SalesChannel channel;
    private Order order;
    private SyncEvent event;

    @BeforeEach
    void setUp() {
        product = ProductFactory.createProduct();
        channel = SalesChannel.builder().id(2L).build();
        order = Order.builder().id(3L).build();
        event = SyncEvent.builder().id(4L).product(product).salesChannel(channel).order(order)
                .timestamp(Instant.now()).status(SyncStatus.SUCCESS).attempts(1).build();
    }

    @Test
    void historyQueriesShouldMapEvents() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findByProductIdOrderByTimestampDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(repository.findByStatus(SyncStatus.FAILURE, pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));

        assertThat(service.findHistoryByProduct(1L, pageable).getContent()).hasSize(1);
        assertThat(service.findFailedEvents(pageable).getContent()).hasSize(1);
    }

    @Test
    void registerEventShouldResolveReferencesAndSave() {
        var dto = new SyncEventInsertDTO(1L, 2L, 3L, "ORDER-1", SyncStatus.SUCCESS, null);
        when(productService.getProductEntityById(1L)).thenReturn(product);
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(orderService.getOrderEntityById(3L)).thenReturn(order);
        when(repository.save(any(SyncEvent.class))).thenAnswer(invocation -> {
            SyncEvent saved = invocation.getArgument(0);
            saved.setId(4L);
            return saved;
        });

        var result = service.registerEvent(dto);

        assertThat(result.id()).isEqualTo(4L);
        assertThat(result.productId()).isEqualTo(1L);
        assertThat(result.orderId()).isEqualTo(3L);
    }

    @Test
    void registerEventShouldRejectFailureWithoutMessage() {
        var dto = new SyncEventInsertDTO(1L, 2L, null, null, SyncStatus.FAILURE, null);
        assertThatThrownBy(() -> service.registerEvent(dto)).isInstanceOf(InvalidSyncEventException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void registerEventShouldRejectErrorMessageForSuccess() {
        var dto = new SyncEventInsertDTO(1L, 2L, null, null, SyncStatus.SUCCESS, "error");
        assertThatThrownBy(() -> service.registerEvent(dto)).isInstanceOf(InvalidSyncEventException.class);
    }

    @Test
    void registerOrderSuccessShouldCreateSuccessEvent() {
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(orderService.getOrderEntityById(3L)).thenReturn(order);

        service.registerOrderSuccess(2L, 3L, "ORDER-1");

        SyncEvent saved = captureSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(saved.getOrder()).isSameAs(order);
        assertThat(saved.getExternalOrderId()).isEqualTo("ORDER-1");
    }

    @Test
    void registerStockSuccessShouldCreateSuccessEvent() {
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);
        when(productService.getProductEntityById(1L)).thenReturn(product);

        service.registerStockSuccess(1L, 2L);

        SyncEvent saved = captureSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(saved.getProduct()).isSameAs(product);
    }

    @Test
    void registerFailureShouldLimitErrorMessageToFiveHundredCharacters() {
        when(salesChannelService.getSalesChannelEntityById(2L)).thenReturn(channel);

        service.registerOrderFailure(2L, "ORDER-1", new RuntimeException("x".repeat(600)));

        SyncEvent saved = captureSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(SyncStatus.FAILURE);
        assertThat(saved.getErrorMessage()).hasSize(500);
    }

    private SyncEvent captureSavedEvent() {
        ArgumentCaptor<SyncEvent> captor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
