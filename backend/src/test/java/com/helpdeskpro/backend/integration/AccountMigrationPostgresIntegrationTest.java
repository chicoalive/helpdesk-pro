package com.helpdeskpro.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "HELPDESK_DISPOSABLE_TEST_PG_URL", matches = ".+")
class AccountMigrationPostgresIntegrationTest {
    private String isolatedDatabase() throws Exception {
        String url = System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL");
        String database = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect(url); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        return url.substring(0, url.lastIndexOf('/') + 1) + database;
    }

    private Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),
                System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));
    }

    private Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),
                        System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"))
                .baselineOnMigrate(true).baselineVersion("1").cleanDisabled(true).load();
    }

    private String value(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private List<String> fingerprints(Connection connection) throws Exception {
        var tables = List.of("users", "assets", "tickets", "ticket_comments", "ticket_audit_logs");
        var result = new java.util.ArrayList<String>();
        for (String table : tables) {
            result.add(value(connection, "SELECT md5(COALESCE(string_agg("
                    + "(to_jsonb(t)-'active'-'session_version'-'edit_version'-'version'-'justification'-'resolved_at'-'sla_resolution_deadline')::text, ',' ORDER BY id),'')) FROM " + table + " t"));
        }
        return result;
    }

    @Test
    void adoptsLegacySchemaPreservingAccountsPasswordsRelationshipsAndAttachments() throws Exception {
        String url = isolatedDatabase();
        try (Connection connection = connect(url)) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__initial_schema.sql"))
                    .populate(connection);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO users (id,name,email,password,role) VALUES
                        (1,'Admin One','first@test.local','unchanged-hash-one','ADMIN'),
                        (2,'Admin Two','second@test.local','unchanged-hash-two','ADMIN'),
                        (3,'Requester','requester@test.local','unchanged-hash-three','REQUESTER'),
                        (4,'Technician','technician@test.local','unchanged-hash-four','TECHNICIAN')
                        """);
                statement.execute("INSERT INTO assets(id,code,status,type) VALUES (1,'MIGRATION-1','IN_USE','Laptop')");
                statement.execute("""
                        INSERT INTO tickets(id,title,description,category,priority,status,created_at,
                        requester_id,technician_id,asset_id,attachment_name,attachment_content_type,attachment_data)
                        VALUES (1,'History','Preserve','DUVIDA','MEDIA','EM_ATENDIMENTO',now(),3,4,1,
                        'test.bin','application/octet-stream',decode('010203ff','hex'))
                        """);
                statement.execute("INSERT INTO ticket_comments(id,content,created_at,author_id,ticket_id) VALUES (1,'Comment',now(),3,1)");
                statement.execute("INSERT INTO ticket_audit_logs(id,changed_at,field_name,changed_by_id,ticket_id) VALUES (1,now(),'status',1,1)");
            }
            var before = fingerprints(connection);
            assertEquals(9, flyway(url).migrate().migrationsExecuted);
            assertEquals(before, fingerprints(connection));
            assertEquals("4", value(connection, "SELECT count(*) FROM users WHERE active AND session_version=0"));
            assertEquals("2", value(connection, "SELECT count(*) FROM users WHERE active AND role='ADMIN'"));
            assertEquals(0, flyway(url).migrate().migrationsExecuted);
            assertEquals(before, fingerprints(connection));
            try (var statement = connection.createStatement()) {
                statement.execute("UPDATE users SET active=false,session_version=session_version+1 WHERE id=4");
            }
            assertEquals("4", value(connection, "SELECT technician_id FROM tickets WHERE id=1"));
            assertEquals("010203ff", value(connection, "SELECT encode(attachment_data,'hex') FROM tickets WHERE id=1"));
            assertEquals("1", value(connection, "SELECT count(*) FROM ticket_comments WHERE author_id=3"));
        }
    }

    @Test
    void createsFreshSchemaWithoutDefaultAccounts() throws Exception {
        String url = isolatedDatabase();
        assertEquals(10, flyway(url).migrate().migrationsExecuted);
        try (Connection connection = connect(url)) {
            assertEquals("0", value(connection, "SELECT count(*) FROM users"));
            assertEquals("2", value(connection, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='users'
                    AND column_name IN ('active','session_version') AND is_nullable='NO'
                    """));
        }
    }
}
