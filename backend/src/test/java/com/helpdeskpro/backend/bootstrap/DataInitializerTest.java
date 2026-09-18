package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes Unitários - DataInitializer")
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("Deve criar o admin quando ele não existe")
    void run_DeveCriarAdminQuandoNaoExiste() {
        when(userRepository.findByEmail("admin@helpdesk.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123")).thenReturn("encoded_123_hash");

        dataInitializer.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User created = captor.getValue();
        assertNotNull(created);
        assertEquals("Administrador Sistema", created.getName());
        assertEquals("admin@helpdesk.com", created.getEmail());
        assertEquals("encoded_123_hash", created.getPassword());
        assertEquals(UserRole.ADMIN, created.getRole());
    }

    @Test
    @DisplayName("Não deve criar usuário duplicado quando o admin já existe")
    void run_NaoDeveCriarUsuarioQuandoAdminJaExiste() {
        User existingAdmin = new User("Administrador Sistema", "admin@helpdesk.com", "existing_encoded_pass", UserRole.ADMIN);
        when(userRepository.findByEmail("admin@helpdesk.com")).thenReturn(Optional.of(existingAdmin));

        dataInitializer.run();

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}
