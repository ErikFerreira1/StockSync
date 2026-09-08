package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.syncEvent.SyncEventInsertDTO;
import com.erikferreira.stocksync.dto.syncEvent.SyncEventResponseDTO;
import com.erikferreira.stocksync.service.SyncEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sync-events")
public class SyncEventController {

    private final SyncEventService service;

    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<SyncEventResponseDTO>> findHistoryByProduct(@PathVariable Long productId, Pageable pageable) {
        return ResponseEntity.ok(service.findHistoryByProduct(productId, pageable));
    }

    @GetMapping("/failed")
    public ResponseEntity<Page<SyncEventResponseDTO>> findFailedEvents(Pageable pageable) {
        return ResponseEntity.ok(service.findFailedEvents(pageable));
    }

    //@PostMapping
    //public ResponseEntity<SyncEventResponseDTO> registerEvent(@Valid @RequestBody SyncEventInsertDTO dto) {
    //    SyncEventResponseDTO response = service.registerEvent(dto);
    //    return ResponseEntity.status(HttpStatus.CREATED).body(response);
    //}

}
