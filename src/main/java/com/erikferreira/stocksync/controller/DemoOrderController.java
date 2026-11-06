package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.demo.DemoMarketplaceListingResponseDTO;
import com.erikferreira.stocksync.dto.demo.DemoOrderRequestDTO;
import com.erikferreira.stocksync.integration.dto.ExternalOrderDTO;
import com.erikferreira.stocksync.service.DemoOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("demo")
@RequiredArgsConstructor
@RequestMapping("/demo/orders")
public class DemoOrderController {
    private final DemoOrderService service;

    @GetMapping("/listings")
    public List<DemoMarketplaceListingResponseDTO> listings(@RequestParam Long salesChannelId) {
        return service.listings(salesChannelId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalOrderDTO simulate(@Valid @RequestBody DemoOrderRequestDTO request) {
        return service.simulate(request);
    }
}
