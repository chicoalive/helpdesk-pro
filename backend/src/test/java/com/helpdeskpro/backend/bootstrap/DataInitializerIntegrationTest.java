package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - DataInitializer")
class DataInitializerIntegrationTest {

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve criar o administrador padrão no banco quando ele não existir")
    void deveCriarAdminQuandoNaoExiste() {
        dataInitializer.run();

        Optional<User> adminOpt = userRepository.findByEmail("admin@helpdesk.com");
        assertTrue(adminOpt.isPresent(), "O admin deveria ter sido criado no banco");

        User admin = adminOpt.get();
        assertEquals("Administrador Sistema", admin.getName());
        assertEquals("admin@helpdesk.com", admin.getEmail());
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertTrue(passwordEncoder.matches("123", admin.getPassword()), "A senha deve corresponder ao hash BCrypt de '123'");
    }

    @Test
    @DisplayName("Não deve criar usuário duplicado se o administrador padrão já existir no banco")
    void naoDeveDuplicarAdminQuandoJaExiste() {
        // Primeira execução cria o admin
        dataInitializer.run();
        long initialCount = userRepository.count();
        assertEquals(1, initialCount);

        // Segunda execução não deve duplicar
        dataInitializer.run();
        long finalCount = userRepository.count();
        assertEquals(1, finalCount);

        List<User> admins = userRepository.findAll();
        assertEquals(1, admins.size());
        assertEquals("admin@helpdesk.com", admins.get(0).getEmail());
    }
}
