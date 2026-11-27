package com.helpdeskpro.backend.bootstrap;

import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Stable receipt of tables and sequences. Only the maintenance journal is excluded. */
public final class MaintenanceSnapshot {
    private MaintenanceSnapshot() {}
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static String fingerprint(JdbcTemplate jdbc) {
        StringBuilder value = new StringBuilder();
        for (String table : jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename <> 'maintenance_audit_logs' ORDER BY tablename", String.class)) {
            if (!table.matches("[a-z_]+")) throw new IllegalStateException("Unsupported table identifier");
            value.append(table).append(':').append(jdbc.queryForObject("SELECT count(*)::text||'|'||md5(COALESCE(string_agg(to_jsonb(t)::text,E'\n' ORDER BY to_jsonb(t)::text),'')) FROM " + table + " t", String.class)).append('\n');
        }
        for (String sequence : jdbc.queryForList("SELECT sequencename FROM pg_sequences WHERE schemaname='public' AND sequencename <> 'maintenance_audit_logs_id_seq' ORDER BY sequencename", String.class)) {
            if (!sequence.matches("[a-z_]+")) throw new IllegalStateException("Unsupported sequence identifier");
            value.append(sequence).append(':').append(jdbc.queryForObject("SELECT last_value::text||'|'||is_called::text FROM " + sequence, String.class)).append('\n');
        }
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }
}
