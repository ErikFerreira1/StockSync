package com.erikferreira.stocksync.service;

import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.order.OrderResponseDTO;
import com.erikferreira.stocksync.dto.orderItem.OrderItemInsertDTO;
import com.erikferreira.stocksync.entity.MarketplaceListing;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.integration.dto.ExternalOrderItemDTO;
import com.erikferreira.stocksync.service.exceptions.InvalidOrderStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderSynchronizationProcessor {

    private final OrderService orderService;
    private final MarketplaceListingService marketplaceListingService;
    private final SyncEventService syncEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOrder(Long salesChannelId, ExternalOrderDTO externalOrder) {
        if (!"paid".equalsIgnoreCase(externalOrder.status())) {
            throw new InvalidOrderStatusException(
                    "Only paid marketplace orders can be imported");
        }
        List<ExternalOrderItemDTO> externalItems = externalOrder.items();
        List<OrderItemInsertDTO> orderItems = new ArrayList<>();

        for (ExternalOrderItemDTO externalItem : externalItems) {
            MarketplaceListing marketplaceListing = marketplaceListingService
                    .getByListingIdAndSalesChannelId(externalItem.listingId(), salesChannelId);

            OrderItemInsertDTO orderItem = new OrderItemInsertDTO(
                    marketplaceListing.getProduct().getId(),
                    externalItem.quantity(),
                    externalItem.unitPrice());

            orderItems.add(orderItem);
        }

        OrderInsertDTO orderInsertDTO = new OrderInsertDTO(
                externalOrder.externalOrderId(),
                salesChannelId,
                externalOrder.orderDate(),
                orderItems);

        OrderResponseDTO orderResponseDTO =  orderService.insert(orderInsertDTO);
        orderResponseDTO = orderService.confirmPayment(orderResponseDTO.id());

        syncEventService.registerOrderSuccess(
                salesChannelId,
                orderResponseDTO.id(),
                orderResponseDTO.externalOrderId());
    }
}
