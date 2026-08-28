package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.integration.MarketplaceIntegrationPort;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@RequiredArgsConstructor
@Validated
@Slf4j
public class OrderSynchronizationService {

    private final MarketplaceIntegrationPort marketplaceIntegrationPort;
    private final OrderService orderService;
    private final OrderSynchronizationProcessor orderSynchronizationProcessor;
    private final SyncEventService syncEventService;

    public void synchronizeOrders(Long salesChannelId) {
        List<ExternalOrderDTO> externalOrders = marketplaceIntegrationPort.fetchNewOrders(salesChannelId);

        for (ExternalOrderDTO externalOrder : externalOrders) {
            try {
                boolean alreadyImported = orderService.existsBySalesChannelIdAndExternalOrderId(
                        salesChannelId,
                        externalOrder.externalOrderId());

                if (!alreadyImported) {
                    orderSynchronizationProcessor.processOrder(salesChannelId, externalOrder);
                }
            } catch (DataIntegrityViolationException ex) {
                boolean alreadyImported = orderService
                        .existsBySalesChannelIdAndExternalOrderId(salesChannelId, externalOrder.externalOrderId());

                if (!alreadyImported) {
                    log.error("Failed to synchronize order {} from sales channel {}",
                            externalOrder.externalOrderId(),
                            salesChannelId,
                            ex);

                    registerFailureEvent(salesChannelId, externalOrder, ex);
                }
            } catch (RuntimeException ex) {
                log.error(
                        "Failed to synchronize order {} from sales channel {}",
                        externalOrder.externalOrderId(),
                        salesChannelId,
                        ex);

                registerFailureEvent(salesChannelId, externalOrder, ex);
            }
        }
    }

    private void registerFailureEvent(Long salesChannelId, ExternalOrderDTO externalOrder, RuntimeException exception) {
        try {
            syncEventService.registerOrderFailure(
                    salesChannelId,
                    externalOrder.externalOrderId(),
                    exception);
        } catch (RuntimeException eventException) {
            log.error(
                    "Failed to register synchronization failure event for order {} from sales channel {}",
                    externalOrder.externalOrderId(),
                    salesChannelId,
                    eventException);
        }
    }
}
