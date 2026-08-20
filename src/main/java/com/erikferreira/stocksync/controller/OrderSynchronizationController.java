package com.erikferreira.stocksync.controller;


import com.erikferreira.stocksync.service.OrderSynchronizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sales-channels/{salesChannelId}/orders")
public class OrderSynchronizationController {

    private final OrderSynchronizationService synchronizationService;

    @PostMapping("/synchronize")
    public ResponseEntity<Void> synchronize(@PathVariable Long salesChannelId) {
        synchronizationService.synchronizeOrders(salesChannelId);
        return ResponseEntity.noContent().build();
    }
}
