package com.helpdeskpro.backend.dto;

import com.helpdeskpro.backend.domain.UserRole;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record UserUpdateRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 2, max = 255, message = "O nome deve ter de 2 a 255 caracteres") String name,
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres") String email,
        @NotNull(message = "Selecione um perfil válido") UserRole role,
        @NotNull(message = "Recarregue os dados antes de editar")
        @PositiveOrZero(message = "Versão de edição inválida") Long expectedVersion) {
    public UserUpdateRequestDTO {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
