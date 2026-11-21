package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.BackendApplication;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELPDESK_DISPOSABLE_TEST_PG_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${HELPDESK_DISPOSABLE_TEST_PG_URL}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${HELPDESK_DISPOSABLE_TEST_PG_USER}",
        "spring.datasource.password=${HELPDESK_DISPOSABLE_TEST_PG_PASSWORD}",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
class FirstAdminPostgresIntegrationTest {
    @Autowired private FirstAdminProvisioner provisioner;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DemoDataInitializer demo;

    @Test
    void serializesProvisioningAndPreservesExistingData() throws Exception {
        assertEquals(0, users.count(), "Use um banco de teste descartável e vazio.");
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(attempt(barrier, "first@example.com"));
            var second = workers.submit(attempt(barrier, "second@example.com"));
            assertNotEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, users.count());
        assertTrue(users.existsByRole(UserRole.ADMIN));

        demo.seedDemoData();
        var tables = List.of("users", "tickets", "ticket_comments", "ticket_audit_logs", "assets");
        var before = tables.stream().map(this::checksum).toList();
        try (var restarted = new SpringApplicationBuilder(BackendApplication.class).run(
                "--server.port=-1",
                "--spring.datasource.url=${HELPDESK_DISPOSABLE_TEST_PG_URL}",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.username=${HELPDESK_DISPOSABLE_TEST_PG_USER}",
                "--spring.datasource.password=${HELPDESK_DISPOSABLE_TEST_PG_PASSWORD}",
                "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.jpa.hibernate.ddl-auto=update")) {
            assertFalse(restarted.getBean(FirstAdminProvisioner.class)
                    .create("Other Admin", "other@example.com", "valid-test-password".toCharArray()));
        }

        assertEquals(before, tables.stream().map(this::checksum).toList());
    }

    private Callable<Boolean> attempt(CyclicBarrier barrier, String email) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return provisioner.create("Installation Admin", email, "valid-test-password".toCharArray());
        };
    }

    private String checksum(String table) {
        return jdbc.queryForObject("SELECT md5(COALESCE(string_agg(t::text, ',' ORDER BY id), '')) FROM "
                + table + " t", String.class);
    }
}
