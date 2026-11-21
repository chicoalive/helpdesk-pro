package com.helpdeskpro.backend.integration;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.dto.UserCreateRequestDTO;
import com.helpdeskpro.backend.repositories.UserAuditLogRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user-audit-rollback;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class UserCreationRollbackIntegrationTest {
    @Autowired private UserService service;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private UserAuditLogRepository audits;

    @Test
    void auditFailureRollsBackTheUserInsert() {
        User actor = users.saveAndFlush(new User("Admin", "rollback-admin@test.local",
                encoder.encode("test-password-strong"), UserRole.ADMIN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
        try {
            long before = users.count();
            when(audits.saveAndFlush(any())).thenThrow(new IllegalStateException("Simulated audit failure"));
            assertThrows(IllegalStateException.class, () -> service.createUser(
                    new UserCreateRequestDTO("Employee", "rollback-employee@test.local",
                            "test-password-strong", UserRole.REQUESTER)));
            assertEquals(before, users.count());
            assertTrue(users.findByEmail("rollback-employee@test.local").isEmpty());
            verify(audits).saveAndFlush(any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void auditFailureRollsBackEditingAndSessionRevocation() {
        User actor = users.saveAndFlush(new User("Admin", "edit-rollback@test.local",
                encoder.encode("test-password-strong"), UserRole.ADMIN));
        User target = users.saveAndFlush(new User("Original", "edit-target@test.local",
                encoder.encode("test-password-strong"), UserRole.TECHNICIAN));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
        try {
            when(audits.saveAndFlush(any())).thenThrow(new IllegalStateException("Simulated audit failure"));
            assertThrows(IllegalStateException.class, () -> service.updateUser(target.getId(),
                    new com.helpdeskpro.backend.dto.UserUpdateRequestDTO("Changed", "changed@test.local",
                            UserRole.REQUESTER, 0L)));
            User preserved = users.findById(target.getId()).orElseThrow();
            assertEquals("Original", preserved.getName());
            assertEquals("edit-target@test.local", preserved.getEmail());
            assertEquals(UserRole.TECHNICIAN, preserved.getRole());
            assertEquals(0, preserved.getSessionVersion());
            assertEquals(0, preserved.getEditVersion());
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void auditFailureRollsBackStateAndSessionVersion() {
        User actor=users.saveAndFlush(new User("Admin", "state-rollback@test.local",encoder.encode("test-password-strong"),UserRole.ADMIN));
        User target=users.saveAndFlush(new User("Target", "state-target@test.local",encoder.encode("test-password-strong"),UserRole.REQUESTER));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));
        try {
            when(audits.saveAndFlush(any())).thenThrow(new IllegalStateException("Simulated audit failure"));
            assertThrows(IllegalStateException.class,()->service.changeState(target.getId(),
                new com.helpdeskpro.backend.dto.UserStateRequestDTO(false,0L)));
            User preserved=users.findById(target.getId()).orElseThrow();
            assertTrue(preserved.isEnabled()); assertEquals(0,preserved.getSessionVersion());
            assertEquals(0,preserved.getEditVersion());
        } finally {SecurityContextHolder.clearContext();}
    }
}
