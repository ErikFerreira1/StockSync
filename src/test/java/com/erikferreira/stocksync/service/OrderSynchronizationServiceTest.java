package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSynchronizationServiceTest {

    @Mock private MarketplaceIntegrationPort marketplaceIntegrationPort;
    @Mock private OrderService orderService;
    @Mock private OrderSynchronizationProcessor processor;
    @Mock private SyncEventService syncEventService;
    @InjectMocks private OrderSynchronizationService service;

    private ExternalOrderDTO order;

    @BeforeEach
    void setUp() {
        order = new ExternalOrderDTO("EXT-1", Instant.now(), "paid", List.of());
    }

    @Test
    void synchronizeOrdersShouldProcessOnlyNewOrders() {
        var imported = new ExternalOrderDTO("EXT-2", Instant.now(), "paid", List.of());
        when(marketplaceIntegrationPort.fetchNewOrders(1L)).thenReturn(List.of(order, imported));
        when(orderService.existsBySalesChannelIdAndExternalOrderId(1L, "EXT-1")).thenReturn(false);
        when(orderService.existsBySalesChannelIdAndExternalOrderId(1L, "EXT-2")).thenReturn(true);

        service.synchronizeOrders(1L);

        verify(processor).processOrder(1L, order);
        verify(processor, never()).processOrder(1L, imported);
    }

    @Test
    void synchronizeOrdersShouldRegisterFailureAndContinue() {
        var second = new ExternalOrderDTO("EXT-2", Instant.now(), "paid", List.of());
        when(marketplaceIntegrationPort.fetchNewOrders(1L)).thenReturn(List.of(order, second));
        when(orderService.existsBySalesChannelIdAndExternalOrderId(1L, "EXT-1")).thenReturn(false);
        when(orderService.existsBySalesChannelIdAndExternalOrderId(1L, "EXT-2")).thenReturn(false);
        RuntimeException failure = new RuntimeException("import failed");
        doThrow(failure).when(processor).processOrder(1L, order);

        service.synchronizeOrders(1L);

        verify(syncEventService).registerOrderFailure(1L, "EXT-1", failure);
        verify(processor).processOrder(1L, second);
    }

    @Test
    void synchronizeOrdersShouldNotAbortWhenFailureEventCannotBeSaved() {
        when(marketplaceIntegrationPort.fetchNewOrders(1L)).thenReturn(List.of(order));
        when(orderService.existsBySalesChannelIdAndExternalOrderId(1L, "EXT-1")).thenReturn(false);
        RuntimeException failure = new RuntimeException("import failed");
        doThrow(failure).when(processor).processOrder(1L, order);
        doThrow(new RuntimeException("event failed"))
                .when(syncEventService).registerOrderFailure(1L, "EXT-1", failure);

        service.synchronizeOrders(1L);

        verify(syncEventService).registerOrderFailure(1L, "EXT-1", failure);
    }

    @Test
    void synchronizeOrdersShouldIgnoreConcurrentDuplicate() {
        when(marketplaceIntegrationPort.fetchNewOrders(1L)).thenReturn(List.of(order));
        when(orderService.existsBySalesChannelIdAndExternalOrderId(1L, "EXT-1"))
                .thenReturn(false, true);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("duplicate order");
        doThrow(failure).when(processor).processOrder(1L, order);

        service.synchronizeOrders(1L);

        verify(orderService, times(2))
                .existsBySalesChannelIdAndExternalOrderId(1L, "EXT-1");
        verify(syncEventService, never()).registerOrderFailure(1L, "EXT-1", failure);
    }

    @Test
    void synchronizeOrdersShouldRegisterFailureWhenIntegrityViolationIsNotDuplicate() {
        when(marketplaceIntegrationPort.fetchNewOrders(1L)).thenReturn(List.of(order));
        when(orderService.existsBySalesChannelIdAndExternalOrderId(1L, "EXT-1"))
                .thenReturn(false, false);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("integrity violation");
        doThrow(failure).when(processor).processOrder(1L, order);

        service.synchronizeOrders(1L);

        verify(orderService, times(2))
                .existsBySalesChannelIdAndExternalOrderId(1L, "EXT-1");
        verify(syncEventService).registerOrderFailure(1L, "EXT-1", failure);
    }
}
