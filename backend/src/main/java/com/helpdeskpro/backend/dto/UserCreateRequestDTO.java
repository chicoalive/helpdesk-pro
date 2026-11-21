package com.helpdeskpro.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.helpdeskpro.backend.domain.UserRole;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record UserCreateRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 2, max = 255, message = "O nome deve ter de 2 a 255 caracteres") String name,
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres") String email,
        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 12, message = "A senha deve ter pelo menos 12 caracteres") String password,
        @NotNull(message = "O perfil do usuário é obrigatório") UserRole role) {

    public UserCreateRequestDTO {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @JsonIgnore
    @AssertTrue(message = "A senha deve ter no máximo 72 bytes em UTF-8")
    public boolean isPasswordByteLengthValid() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "UserCreateRequestDTO[conteúdo protegido]";
    }
}
