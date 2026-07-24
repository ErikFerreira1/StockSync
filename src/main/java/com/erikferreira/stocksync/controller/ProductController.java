package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.product.ProductInsertDTO;
import com.erikferreira.stocksync.dto.product.ProductResponseDTO;
import com.erikferreira.stocksync.dto.product.ProductUpdateDTO;
import com.erikferreira.stocksync.service.ProductService;
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
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    @GetMapping
    public ResponseEntity<Page<ProductResponseDTO>> findAllPaged(Pageable pageable) {
        Page<ProductResponseDTO> dto = service.findAllPaged(pageable);

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> findById(@PathVariable Long id) {
        ProductResponseDTO dto = service.findById(id);

        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> insert(@Valid @RequestBody ProductInsertDTO insertDTO) {
        ProductResponseDTO dto =  service.insert(insertDTO);

        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(dto.id())
                .toUri();

        return ResponseEntity.created(uri).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateDTO updateDTO) {
        ProductResponseDTO dto = service.update(id, updateDTO);

        return ResponseEntity.ok(dto);
    }


    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        service.activate(id);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        service.hardDelete(id);

        return ResponseEntity.noContent().build();
    }

}
