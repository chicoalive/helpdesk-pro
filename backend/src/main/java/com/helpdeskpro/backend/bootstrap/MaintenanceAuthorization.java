package com.helpdeskpro.backend.bootstrap;

import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
import java.util.*;

/** The trust file is mounted read-only by the authorized OS operator; never accepted by HTTP. */
public final class MaintenanceAuthorization {
    private MaintenanceAuthorization() {}
    public static final class Verified {
        final UUID requestId; final String operatorSid, operatorName, email, reason, backupHash, fingerprint;
        final long userId, sessionVersion, editVersion; final String password;
        private Verified(tools.jackson.databind.JsonNode p) {
            requestId=UUID.fromString(p.path("requestId").asText()); operatorSid=p.path("operatorSid").asText();
            operatorName=p.path("operatorName").asText(); email=p.path("email").asText(); reason=p.path("reason").asText();
            backupHash=p.path("backupSha256").asText(); fingerprint=p.path("fingerprint").asText();
            userId=p.path("userId").asLong(); sessionVersion=p.path("sessionVersion").asLong();editVersion=p.path("editVersion").asLong();password=p.path("password").asText();
        }
        @Override public String toString(){return "VerifiedMaintenance[protected]";}
    }
    public static Verified verify(String envelope, Path trustFile, Path backupFile, JdbcTemplate jdbc, Clock clock) {
        try {
            if(envelope.length()>24000)throw new IllegalArgumentException();
            Properties trust=new Properties();try(var input=Files.newInputStream(trustFile)){trust.load(input);}
            var json=JsonMapper.builder().build();var root=json.readTree(envelope);
            byte[] payload=Base64.getDecoder().decode(root.path("payload").asText());
            if(payload.length>12000)throw new IllegalArgumentException();
            Signature verifier=Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(trust.getProperty("publicKey")))));
            verifier.update(payload);if(!verifier.verify(Base64.getDecoder().decode(root.path("signature").asText())))throw new IllegalArgumentException();
            var p=json.readTree(new String(payload,StandardCharsets.UTF_8));
            Instant issued=Instant.parse(p.path("issuedAt").asText());
            if(issued.isAfter(clock.instant().plusSeconds(30))||issued.isBefore(clock.instant().minusSeconds(300)))throw new IllegalArgumentException();
            if(!"recover-admin".equals(p.path("operation").asText())||!trust.getProperty("operatorSid").equals(p.path("operatorSid").asText()))throw new IllegalArgumentException();
            if(!jdbc.queryForObject("SELECT current_database()",String.class).equals(p.path("database").asText())
                ||!jdbc.queryForObject("SELECT installation_id::text FROM maintenance_installation WHERE id=1",String.class).equals(p.path("installationId").asText())
                ||!jdbc.queryForObject("SELECT system_identifier::text FROM pg_control_system()",String.class).equals(p.path("systemId").asText()))throw new IllegalArgumentException();
            if(!p.path("restoreVerified").asBoolean()||!Files.isRegularFile(backupFile))throw new IllegalArgumentException();
            // The operator has already restored and compared this dump; verify its exact bytes again.
            try(var stream=Files.newInputStream(backupFile)) {
                if(!Arrays.equals(stream.readNBytes(5),"PGDMP".getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();
            }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(backupFile)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
            if(!HexFormat.of().formatHex(digest.digest()).equals(p.path("backupSha256").asText()))throw new IllegalArgumentException();
            Verified result=new Verified(p);
            if(result.userId<1||result.email.isBlank()||result.reason.isBlank()||result.reason.length()>255||result.operatorName.isBlank()||result.operatorName.length()>255||result.operatorSid.length()>184||!result.fingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
            return result;
        } catch(Exception e) {throw new IllegalArgumentException("Autorização local, ambiente ou backup inválido.");}
    }
}
