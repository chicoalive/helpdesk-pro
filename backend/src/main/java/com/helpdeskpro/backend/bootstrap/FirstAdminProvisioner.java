package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.repositories.UserRepository;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class FirstAdminProvisioner {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Validator validator;

    public FirstAdminProvisioner(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder,
                                 Validator validator) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.validator = validator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean create(String name, String email, char[] password) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        String normalizedName = name.strip();
        String rawPassword = new String(password);
        if (normalizedName.length() > 255 || normalizedEmail.length() > 255 || rawPassword.isBlank()
                || rawPassword.codePointCount(0, rawPassword.length()) < 12
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Nome, e-mail ou senha fora dos limites permitidos.");
        }
        User admin = new User(normalizedName, normalizedEmail, rawPassword, UserRole.ADMIN);
        if (!validator.validate(admin).isEmpty()) {
            throw new IllegalArgumentException("Informe um nome e um e-mail válidos.");
        }
        admin.setPassword(encoder.encode(rawPassword));

        // Serialize the check and insert, including concurrent writes from other app instances.
        jdbc.execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
        if (users.existsByRole(UserRole.ADMIN) || users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            return false;
        }
        users.save(admin);
        return true;
    }
}