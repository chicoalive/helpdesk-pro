package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:first-admin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class FirstAdminProvisionerIntegrationTest {
    @Autowired private FirstAdminProvisioner provisioner;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    // H2 lacks PostgreSQL's table-lock statement.
    @MockitoBean private JdbcTemplate jdbc;

    @Test
    void startsWithoutDefaultAccountsAndProvisionsOnlyOnce() {
        assertEquals(0, users.count());
        assertThrows(IllegalArgumentException.class,
                () -> provisioner.create("Operador", "invalid", "a-long-test-password".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> provisioner.create("Operador", "operator@example.com", "short".toCharArray()));
        assertEquals(0, users.count());

        var requester = users.save(new User("Employee", "employee@example.com", "existing-hash", UserRole.REQUESTER));
        assertFalse(provisioner.create("Operator", " EMPLOYEE@example.com ", "a-long-test-password".toCharArray()));
        assertTrue(provisioner.create(" Operator ", " OPERATOR@example.com ", "a-long-test-password".toCharArray()));
        var admin = users.findByEmail("operator@example.com").orElseThrow();
        assertEquals("Operator", admin.getName());
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertTrue(encoder.matches("a-long-test-password", admin.getPassword()));
        assertFalse(provisioner.create("Other", "other@example.com", "another-test-password".toCharArray()));
        assertEquals(2, users.count());
        assertEquals("existing-hash", users.findById(requester.getId()).orElseThrow().getPassword());
        assertEquals(admin.getPassword(), users.findById(admin.getId()).orElseThrow().getPassword());
        verify(jdbc, times(3)).execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
    }
}
