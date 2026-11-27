package com.helpdeskpro.backend.integration;

import com.helpdeskpro.backend.bootstrap.*;
import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.domain.entities.PasswordRecovery;
import com.helpdeskpro.backend.repositories.*;
import com.helpdeskpro.backend.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.web.context.WebApplicationContext;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="HELPDESK_DISPOSABLE_TEST_PG_URL",matches=".+")
class LocalMaintenancePostgresIntegrationTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception{
  String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),db="maintenance_"+UUID.randomUUID().toString().replace("-","");
  try(var c=DriverManager.getConnection(base,System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
  r.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+db);r.add("spring.datasource.username",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"));r.add("spring.datasource.password",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->true);
 }
 @Autowired LocalAdminRecovery recovery;@Autowired JdbcTemplate jdbc;@Autowired UserRepository users;@Autowired PasswordRecoveryRepository requests;@Autowired PasswordEncoder encoder;@Autowired TokenService tokens;@Autowired WebApplicationContext web;
 @TempDir Path directory;Path trust,backup;KeyPair keys;User admin,other;String oldPassword,newPassword;MockMvc mvc;final ObjectMapper json=new ObjectMapper();
 @BeforeEach void setup()throws Exception{
  oldPassword="Old1!"+UUID.randomUUID();newPassword="New2!"+UUID.randomUUID();admin=users.saveAndFlush(new User("Administrador laboratório",UUID.randomUUID()+"@lab.test",encoder.encode(oldPassword),UserRole.ADMIN));other=users.saveAndFlush(new User("Outro usuário",UUID.randomUUID()+"@lab.test",encoder.encode(oldPassword),UserRole.REQUESTER));
  var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair();trust=directory.resolve("trust.properties");backup=directory.resolve("database.dump");Files.writeString(backup,"PGDMP-fixture-for-authorized-receipt-tests");Files.writeString(trust,"operatorSid=S-1-5-21-123\npublicKey="+Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));mvc=MockMvcBuilders.webAppContextSetup(web).apply(SecurityMockMvcConfigurers.springSecurity()).build();
 }
 Map<String,Object> body(){var p=new HashMap<String,Object>();p.put("operation","recover-admin");p.put("requestId",UUID.randomUUID().toString());p.put("issuedAt",Instant.now().toString());p.put("operatorSid","S-1-5-21-123");p.put("operatorName","Lab\\Operator");p.put("database",jdbc.queryForObject("SELECT current_database()",String.class));p.put("systemId",jdbc.queryForObject("SELECT system_identifier::text FROM pg_control_system()",String.class));p.put("installationId",jdbc.queryForObject("SELECT installation_id::text FROM maintenance_installation",String.class));p.put("backupSha256",hashFile());p.put("fingerprint",MaintenanceSnapshot.fingerprint(jdbc));p.put("restoreVerified",true);p.put("userId",admin.getId());p.put("email",admin.getEmail());p.put("sessionVersion",admin.getSessionVersion());p.put("editVersion",admin.getEditVersion());p.put("password",newPassword);p.put("reason","Exercício autorizado em laboratório");return p;}
 String hashFile(){try{return MaintenanceSnapshot.sha256(Files.readAllBytes(backup));}catch(Exception e){throw new RuntimeException(e);}}
 String signed(Map<String,Object> p)throws Exception{byte[] bytes=json.writeValueAsBytes(p);var signature=Signature.getInstance("SHA256withRSA");signature.initSign(keys.getPrivate());signature.update(bytes);return json.writeValueAsString(Map.of("payload",Base64.getEncoder().encodeToString(bytes),"signature",Base64.getEncoder().encodeToString(signature.sign())));}
 MaintenanceAuthorization.Verified verified(Map<String,Object> p)throws Exception{return MaintenanceAuthorization.verify(signed(p),trust,backup,jdbc,Clock.systemUTC());}
 void failAuthorization(String key,Object value)throws Exception{var p=body();p.put(key,value);assertThrows(IllegalArgumentException.class,()->verified(p));assertTrue(encoder.matches(oldPassword,users.findById(admin.getId()).orElseThrow().getPassword()));}
 int login(String password)throws Exception{return mvc.perform(post("/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",admin.getEmail(),"password",password)))).andReturn().getResponse().getStatus();}
 @Test void successfulRecoveryRevokesOldPasswordTokensAndPendingAuthorizationWithoutTouchingOthers()throws Exception{
  var r=new PasswordRecovery();r.user=admin;r.requestedAt=Instant.now();r.status="APPROVED";r.sessionVersion=0;r.tokenHash="a".repeat(64);r.expiresAt=Instant.now().plusSeconds(900);requests.saveAndFlush(r);
  String beforeOther=jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u WHERE id=?",String.class,other.getId());String oldJwt=tokens.generateToken(admin);long audits=jdbc.queryForObject("SELECT count(*) FROM maintenance_audit_logs",Long.class);
  recovery.recover(verified(body()));assertEquals(200,login(newPassword));assertEquals(401,login(oldPassword));assertEquals(401,mvc.perform(get("/users").header("Authorization","Bearer "+oldJwt)).andReturn().getResponse().getStatus());
  User updated=users.findById(admin.getId()).orElseThrow();assertEquals(1,updated.getSessionVersion());assertTrue(updated.isEnabled());assertEquals(UserRole.ADMIN,updated.getRole());assertEquals("REVOKED",requests.findById(r.id).orElseThrow().status);assertNull(requests.findById(r.id).orElseThrow().tokenHash);assertEquals(beforeOther,jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u WHERE id=?",String.class,other.getId()));assertEquals(audits+1,jdbc.queryForObject("SELECT count(*) FROM maintenance_audit_logs",Long.class));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM maintenance_audit_logs WHERE target_user_id=? AND result='SUCCESS' AND operator_sid='S-1-5-21-123'",Integer.class,admin.getId()));
  String log=jdbc.queryForObject("SELECT to_jsonb(t)::text FROM maintenance_audit_logs t WHERE target_user_id=?",String.class,admin.getId());assertFalse(log.contains(newPassword));assertFalse(log.contains(oldJwt));assertFalse(log.contains("actor_id"));
 }
 @Test void unauthorizedOperatorIsRejected()throws Exception{failAuthorization("operatorSid","S-1-5-21-not-authorized");}
 @Test void invalidSignatureIsRejected()throws Exception{var p=body();String envelope=signed(p);var parsed=json.readTree(envelope);((com.fasterxml.jackson.databind.node.ObjectNode)parsed).put("signature",Base64.getEncoder().encodeToString(new byte[256]));assertThrows(IllegalArgumentException.class,()->MaintenanceAuthorization.verify(parsed.toString(),trust,backup,jdbc,Clock.systemUTC()));}
 @Test void wrongEnvironmentIsRejected()throws Exception{failAuthorization("installationId",UUID.randomUUID().toString());failAuthorization("systemId","0");failAuthorization("database","not-this-installation");}
 @Test void expiredRequestAndWrongOperationAreRejected()throws Exception{failAuthorization("issuedAt",Instant.now().minusSeconds(301).toString());failAuthorization("operation","provision-admin");}
 @Test void missingOrTamperedBackupIsRejected()throws Exception{var p=body();Files.writeString(backup,"PGDMP-changed");assertThrows(IllegalArgumentException.class,()->verified(p));Files.delete(backup);assertThrows(IllegalArgumentException.class,()->verified(p));}
 @Test void unverifiedRestoreIsRejected()throws Exception{failAuthorization("restoreVerified",false);}
 @Test void incorrectTargetIsRefusedAndFailureIsAudited()throws Exception{var p=body();p.put("email",other.getEmail());assertThrows(IllegalStateException.class,()->recovery.recover(verified(p)));assertEquals(200,login(oldPassword));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM maintenance_audit_logs WHERE target_user_id=? AND result='FAILED'",Integer.class,admin.getId()));}
 @Test void nonAdministratorAndMissingAccountAreRejected()throws Exception{var p=body();p.put("userId",other.getId());p.put("email",other.getEmail());assertThrows(IllegalStateException.class,()->recovery.recover(verified(p)));p.put("userId",Long.MAX_VALUE);assertThrows(IllegalStateException.class,()->recovery.recover(verified(p)));}
 @Test void inactiveAdministratorIsNeverReactivated()throws Exception{admin.setActive(false);users.saveAndFlush(admin);var p=body();p.put("sessionVersion",admin.getSessionVersion());p.put("editVersion",admin.getEditVersion());assertThrows(IllegalStateException.class,()->recovery.recover(verified(p)));assertFalse(users.findById(admin.getId()).orElseThrow().isEnabled());}
 @Test void weakPasswordAndStaleBackupDoNotModifyAccount()throws Exception{var p=body();p.put("password","short");assertThrows(IllegalStateException.class,()->recovery.recover(verified(p)));var stale=body();stale.put("fingerprint","0".repeat(64));assertThrows(IllegalStateException.class,()->recovery.recover(verified(stale)));assertEquals(200,login(oldPassword));}
 @Test void failedSuccessAuditRollsBackPasswordSessionAndAuthorization()throws Exception{String before=jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u WHERE id=?",String.class,admin.getId());jdbc.execute("CREATE FUNCTION reject_maintenance() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.result='SUCCESS' THEN RAISE EXCEPTION 'simulated outage'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER reject_maintenance BEFORE INSERT ON maintenance_audit_logs FOR EACH ROW EXECUTE FUNCTION reject_maintenance()");try{assertThrows(IllegalStateException.class,()->recovery.recover(verified(body())));assertEquals(before,jdbc.queryForObject("SELECT to_jsonb(u)::text FROM users u WHERE id=?",String.class,admin.getId()));assertEquals(200,login(oldPassword));}finally{jdbc.execute("DROP TRIGGER reject_maintenance ON maintenance_audit_logs");jdbc.execute("DROP FUNCTION reject_maintenance()");}}
 @Test void concurrentRecoveryHasExactlyOneWinnerAndNoReplay()throws Exception{var first=verified(body());var second=verified(body());var gate=new CyclicBarrier(2);try(var pool=Executors.newFixedThreadPool(2)){Callable<Boolean> a=()->{gate.await();try{recovery.recover(first);return true;}catch(IllegalStateException e){return false;}};Callable<Boolean> b=()->{gate.await();try{recovery.recover(second);return true;}catch(IllegalStateException e){return false;}};var one=pool.submit(a);var two=pool.submit(b);assertNotEquals(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));}assertEquals(200,login(newPassword));assertThrows(IllegalStateException.class,()->recovery.recover(first));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM maintenance_audit_logs WHERE target_user_id=? AND result='SUCCESS'",Integer.class,admin.getId()));}
 @Test void v10UpgradeKeepsExistingDataAndAddsNoMaintenanceEvents()throws Exception{String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),db="maintenance_upgrade_"+UUID.randomUUID().toString().replace("-","");String user=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),password=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD");try(var c=DriverManager.getConnection(base,user,password);var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}String url=base.substring(0,base.lastIndexOf('/')+1)+db;org.flywaydb.core.Flyway.configure().dataSource(url,user,password).target("10").load().migrate();try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("INSERT INTO users(name,email,password,role,active) VALUES('Existing','existing@lab.test','not-a-real-hash','ADMIN',true)");String before;try(var r=s.executeQuery("SELECT to_jsonb(u)::text FROM users u")){r.next();before=r.getString(1);}assertEquals(1,org.flywaydb.core.Flyway.configure().dataSource(url,user,password).load().migrate().migrationsExecuted);try(var r=s.executeQuery("SELECT to_jsonb(u)::text FROM users u")){r.next();assertEquals(before,r.getString(1));}try(var r=s.executeQuery("SELECT count(*) FROM maintenance_audit_logs")){r.next();assertEquals(0,r.getLong(1));}}}
}
