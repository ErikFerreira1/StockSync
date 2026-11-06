package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.syncEvent.SyncEventFilterDTO;
import com.erikferreira.stocksync.dto.syncEvent.SyncEventResponseDTO;
import com.erikferreira.stocksync.service.SyncEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sync-events")
public class SyncEventController {

    private final SyncEventService service;

    @GetMapping
    public ResponseEntity<Page<SyncEventResponseDTO>> search(@Valid @ModelAttribute SyncEventFilterDTO filter,
            @PageableDefault(size = 20, sort = {"timestamp", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.search(filter, pageable));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<SyncEventResponseDTO>> findHistoryByProduct(@PathVariable Long productId, Pageable pageable) {
        return ResponseEntity.ok(service.findHistoryByProduct(productId, pageable));
    }

    @GetMapping("/failed")
    public ResponseEntity<Page<SyncEventResponseDTO>> findFailedEvents(Pageable pageable) {
        return ResponseEntity.ok(service.findFailedEvents(pageable));
    }

}
