package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String ADMIN_EMAIL = "admin@helpdesk.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            User admin = new User(
                    "Administrador Sistema",
                    ADMIN_EMAIL,
                    passwordEncoder.encode("123"),
                    UserRole.ADMIN
            );
            userRepository.save(admin);
            log.info("Administrador padrão criado com sucesso: {}", ADMIN_EMAIL);
        } else {
            log.info("Administrador padrão já existente: {}", ADMIN_EMAIL);
        }
    }
}
