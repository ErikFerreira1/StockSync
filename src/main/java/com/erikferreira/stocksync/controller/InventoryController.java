package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.inventory.InventoryResponseDTO;
import com.erikferreira.stocksync.dto.inventory.UpdateMinQuantityDTO;
import com.erikferreira.stocksync.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService service;

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponseDTO> findByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(service.findByProduct(productId));
    }

    @GetMapping("/below-minimum")
    public ResponseEntity<List<InventoryResponseDTO>> findProductsBelowMinimum() {
        return ResponseEntity.ok(service.findProductsBelowMinimum());
    }

    @PatchMapping("/product/{productId}/min-quantity")
    public ResponseEntity<InventoryResponseDTO> updateMinQuantity(@PathVariable Long productId, @Valid @RequestBody UpdateMinQuantityDTO dto) {
        return ResponseEntity.ok(service.updateMinQuantity(productId, dto));
    }







}
