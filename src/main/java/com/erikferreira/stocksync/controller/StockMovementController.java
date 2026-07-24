package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.stockmovement.StockMovementInsertDTO;
import com.erikferreira.stocksync.dto.stockmovement.StockMovementResponseDTO;
import com.erikferreira.stocksync.service.StockMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RestController
@RequiredArgsConstructor
@RequestMapping("/stock-movement")
public class StockMovementController {

    private final StockMovementService service;

    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<StockMovementResponseDTO>> findHistoryByProduct(@PathVariable Long productId, Pageable pageable){
        return ResponseEntity.ok(service.findHistoryByProduct(productId, pageable));
    }

    @PostMapping
    public ResponseEntity<StockMovementResponseDTO> registerMovement(@Valid @RequestBody StockMovementInsertDTO dto) {
        StockMovementResponseDTO response = service.registerMovement(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }



}
