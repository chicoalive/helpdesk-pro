package com.helpdeskpro.backend.controllers;

import com.helpdeskpro.backend.dto.UserCreateRequestDTO;
import com.helpdeskpro.backend.dto.UserResponseDTO;
import com.helpdeskpro.backend.dto.UserUpdateRequestDTO;
import com.helpdeskpro.backend.services.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserResponseDTO createUser(@Valid @RequestBody UserCreateRequestDTO request) {
        return UserResponseDTO.fromEntity(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public UserResponseDTO updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequestDTO request) {
        return UserResponseDTO.fromEntity(userService.updateUser(id, request));
    }

    @PatchMapping("/{id}/state")
    public UserResponseDTO changeState(@PathVariable Long id,
            @Valid @RequestBody com.helpdeskpro.backend.dto.UserStateRequestDTO request) {
        return UserResponseDTO.fromEntity(userService.changeState(id, request));
    }

    @GetMapping
    public List<UserResponseDTO> getAllUsers() {
        return userService.getAllUsers().stream().map(UserResponseDTO::fromEntity).toList();
    }
}
