package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.order.OrderInsertDTO;
import com.erikferreira.stocksync.dto.order.OrderResponseDTO;
import com.erikferreira.stocksync.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    @GetMapping
    public ResponseEntity<Page<OrderResponseDTO>> findAllPaged(Pageable pageable) {
        return ResponseEntity.ok(service.findAllPaged(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponseDTO> insert(@Valid @RequestBody OrderInsertDTO insertDTO) {
        OrderResponseDTO dto = service.insert(insertDTO);

        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(dto.id())
                .toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDTO> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancelOrder(id));
    }

}
