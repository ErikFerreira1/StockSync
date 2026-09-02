package com.erikferreira.stocksync.controller;

import com.erikferreira.stocksync.dto.user.UserInsertDTO;
import com.erikferreira.stocksync.dto.user.UserResponseDTO;
import com.erikferreira.stocksync.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    @PostMapping
    public ResponseEntity<UserResponseDTO> insert(@Valid @RequestBody UserInsertDTO insertDTO) {
        UserResponseDTO dto = service.insert(insertDTO);

        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(dto.id())
                .toUri();

        return ResponseEntity.created(uri).body(dto);
    }
}
