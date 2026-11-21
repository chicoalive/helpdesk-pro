package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;

public record UserResponseDTO(Long id, String name, String email, UserRole role, boolean active, long version) {
    public static UserResponseDTO fromEntity(User user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isEnabled(), user.getEditVersion());
    }
}
