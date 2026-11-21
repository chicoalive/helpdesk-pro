package com.helpdeskpro.backend.integration;
import com.fasterxml.jackson.databind.*;
import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.repositories.*;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest
@EnabledIfEnvironmentVariable(named="HELPDESK_DISPOSABLE_TEST_PG_URL",matches=".+")
class PasswordRecoveryPostgresIntegrationTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception{
  String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),name="recovery_"+UUID.randomUUID().toString().replace("-","");
  try(var c=DriverManager.getConnection(base,System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
  r.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+name);r.add("spring.datasource.username",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"));r.add("spring.datasource.password",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));
  r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->true);
 }
 @Autowired WebApplicationContext context;@Autowired UserRepository users;@Autowired UserAuditLogRepository audits;
 @Autowired PasswordRecoveryRepository requests;@Autowired PasswordRecoveryRateRepository rates;@Autowired TokenService tokens;@Autowired PasswordEncoder encoder;@Autowired JdbcTemplate jdbc;
 @MockitoBean Clock clock;
 MockMvc mvc;ObjectMapper json=new ObjectMapper();User admin,target;String jwt;
 static final String OLD="SenhaTeste123456!",NEW="SenhaNova2026Forte!";
 static final Instant NOW=Instant.parse("2026-11-21T15:00:00Z");
 @BeforeEach void setup(){when(clock.instant()).thenReturn(NOW);rates.deleteAll();admin=create(UserRole.ADMIN);target=create(UserRole.REQUESTER);jwt=tokens.generateToken(admin);mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();}
 User create(UserRole role){return users.saveAndFlush(new User("Homologação",UUID.randomUUID()+"@empresa.test",encoder.encode(OLD),role));}
 org.springframework.mock.web.MockHttpServletResponse call(String method,String path,Object body,String token,int expected)throws Exception{
  var req=method.equals("GET")?get(path):post(path);if(token!=null)req.header("Authorization","Bearer "+token);if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  var response=mvc.perform(req).andReturn().getResponse();assertEquals(expected,response.getStatus(),path);return response;
 }
 long request(User user)throws Exception{call("POST","/auth/password-recovery",Map.of("email",user.getEmail()),null,202);return requests.findByUserIdAndStatusIn(user.getId(),List.of("PENDING")).getFirst().id;}
 String approve(long id)throws Exception{var row=requests.findById(id).orElseThrow();var response=call("POST","/admin/password-recoveries/"+id+"/approve",Map.of("expectedVersion",row.version,"identityVerified",true),jwt,200);assertTrue(response.getHeader("Cache-Control").contains("no-store"));return json.readTree(response.getContentAsString()).get("authorization").asText();}
 void reset(String code,String pwd,String confirmation,int status)throws Exception{call("POST","/auth/password-reset",Map.of("authorization",code,"password",pwd,"confirmation",confirmation),null,status);}
 @Test void registeredUnknownInactiveAndDuplicateHaveIdenticalPublicResponses()throws Exception{
  var known=call("POST","/auth/password-recovery",Map.of("email",target.getEmail()),null,202).getContentAsString();
  var unknown=call("POST","/auth/password-recovery",Map.of("email","unknown@empresa.test"),null,202).getContentAsString();
  assertEquals(known,unknown);call("POST","/auth/password-recovery",Map.of("email",target.getEmail()),null,202);
  assertEquals(1,requests.findByUserIdAndStatusIn(target.getId(),List.of("PENDING")).size());
  User inactive=create(UserRole.REQUESTER);inactive.setActive(false);users.saveAndFlush(inactive);
  assertEquals(known,call("POST","/auth/password-recovery",Map.of("email",inactive.getEmail()),null,202).getContentAsString());
  assertTrue(requests.findByUserIdAndStatusIn(inactive.getId(),List.of("PENDING")).isEmpty());
 }
 @ParameterizedTest @EnumSource(value=UserRole.class,names={"TECHNICIAN","REQUESTER"})
 void ordinaryProfilesCannotListApproveRejectOrRevoke(UserRole role)throws Exception{
  long id=request(target);String token=tokens.generateToken(create(role));
  call("GET","/admin/password-recoveries",null,token,403);
  for(String action:List.of("approve","reject","revoke"))call("POST","/admin/password-recoveries/"+id+"/"+action,Map.of("expectedVersion",0,"identityVerified",true),token,403);
 }
 @Test void approvalRequiresIdentityAndRejectionAuditsWithoutAuthorization()throws Exception{
  long id=request(target);call("POST","/admin/password-recoveries/"+id+"/approve",Map.of("expectedVersion",0,"identityVerified",false),jwt,400);
  call("POST","/admin/password-recoveries/"+id+"/reject",Map.of("expectedVersion",0),jwt,200);
  assertEquals("REJECTED",requests.findById(id).orElseThrow().status);assertNull(requests.findById(id).orElseThrow().tokenHash);
  assertEquals(2,audits.findByUserIdOrderByOccurredAtAsc(target.getId()).size());
 }
 @Test void successfulResetRevokesJwtAndCannotBeReused()throws Exception{
  String oldJwt=tokens.generateToken(target);long id=request(target);String code=approve(id);
  assertEquals(43,code.length());var pending=requests.findById(id).orElseThrow();assertFalse(code.equals(pending.tokenHash));assertEquals(64,pending.tokenHash.length());
  String list=call("GET","/admin/password-recoveries",null,jwt,200).getContentAsString();assertFalse(list.contains(code));assertFalse(list.contains(pending.tokenHash));
  reset(code,"short","short",400);reset(code,NEW,"different",400);reset(code,NEW,NEW,200);reset(code,NEW,NEW,400);
  call("GET","/assets/available",null,oldJwt,401);call("POST","/auth/login",Map.of("email",target.getEmail(),"password",OLD),null,401);call("POST","/auth/login",Map.of("email",target.getEmail(),"password",NEW),null,200);
  assertEquals("USED",requests.findById(id).orElseThrow().status);assertNull(requests.findById(id).orElseThrow().tokenHash);
  var events=audits.findByUserIdOrderByOccurredAtAsc(target.getId());assertEquals(List.of("PASSWORD_RECOVERY_REQUESTED","PASSWORD_RECOVERY_APPROVED","PASSWORD_RESET_AUTHORIZATION_ISSUED","PASSWORD_RESET_COMPLETED"),events.stream().map(e->e.getOperation()).toList());
  assertNotNull(events.get(1).getActor());assertEquals(admin.getId(),events.get(1).getActor().getId());for(var event:events){assertNotNull(event.getOccurredAt());assertEquals(id,event.getRecoveryRequestId());}
 }
 @Test void wrongExpiredRevokedAndInactiveTokensAreRejected()throws Exception{
  long id=request(target);String code=approve(id);reset("X".repeat(43),NEW,NEW,400);
  when(clock.instant()).thenReturn(NOW.plusSeconds(901));reset(code,NEW,NEW,400);when(clock.instant()).thenReturn(NOW);
  var r=requests.findById(id).orElseThrow();call("POST","/admin/password-recoveries/"+id+"/revoke",Map.of("expectedVersion",r.version),jwt,200);reset(code,NEW,NEW,400);
  User inactive=create(UserRole.TECHNICIAN);long other=request(inactive);String token=approve(other);inactive.setActive(false);users.saveAndFlush(inactive);reset(token,NEW,NEW,400);
  inactive=users.findById(inactive.getId()).orElseThrow();inactive.setActive(true);users.saveAndFlush(inactive);rates.deleteAll();reset(token,NEW,NEW,400);
  assertTrue(encoder.matches(OLD,users.findById(target.getId()).orElseThrow().getPassword()));
 }
 @Test void rateLimitsPersistAcrossFailedResetTransactions()throws Exception{
  for(int i=0;i<5;i++)reset("X".repeat(43),NEW,NEW,400);
  reset("X".repeat(43),NEW,NEW,429);when(clock.instant()).thenReturn(NOW.plusSeconds(61));reset("X".repeat(43),NEW,NEW,400);
  for(int i=0;i<5;i++)call("POST","/auth/password-recovery",Map.of("email",target.getEmail()),null,202);
  assertEquals(1,requests.findByUserIdAndStatusIn(target.getId(),List.of("PENDING")).size());
 }
 @Test void sensitiveAccountChangeInvalidatesAuthorization()throws Exception{
  long id=request(target);String code=approve(id);target.revokeSessions();target.setEmail(UUID.randomUUID()+"@empresa.test");users.saveAndFlush(target);
  reset(code,NEW,NEW,400);
 }
 @Test void auditFailureRollsBackPasswordAndRequest()throws Exception{
  long id=request(target);String code=approve(id);String hash=users.findById(target.getId()).orElseThrow().getPassword();long count=audits.count();
  jdbc.execute("CREATE FUNCTION fail_reset_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.operation='PASSWORD_RESET_COMPLETED' THEN RAISE EXCEPTION 'Simulated audit outage'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER fail_reset_audit BEFORE INSERT ON user_audit_logs FOR EACH ROW EXECUTE FUNCTION fail_reset_audit()");
  try{reset(code,NEW,NEW,500);assertTrue(hash.equals(users.findById(target.getId()).orElseThrow().getPassword()));assertEquals(count,audits.count());assertEquals("APPROVED",requests.findById(id).orElseThrow().status);assertEquals(0,users.findById(target.getId()).orElseThrow().getSessionVersion());}
  finally{jdbc.execute("DROP TRIGGER fail_reset_audit ON user_audit_logs");jdbc.execute("DROP FUNCTION fail_reset_audit()");}
 }
 @Test void concurrentResetConsumesOnlyOnce()throws Exception{
  String code=approve(request(target));var barrier=new CyclicBarrier(2);
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Integer> attempt=()->{barrier.await(10,TimeUnit.SECONDS);return mvc.perform(post("/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("authorization",code,"password",NEW,"confirmation",NEW)))).andReturn().getResponse().getStatus();};
   var a=pool.submit(attempt);var b=pool.submit(attempt);var statuses=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));assertTrue(statuses.contains(200),statuses.toString());assertTrue(statuses.contains(400)||statuses.contains(409),statuses.toString());
  }
  assertEquals(1,users.findById(target.getId()).orElseThrow().getSessionVersion());assertEquals(1,audits.findByUserIdOrderByOccurredAtAsc(target.getId()).stream().filter(e->e.getOperation().equals("PASSWORD_RESET_COMPLETED")).count());
 }
 @Test void onlyOneConcurrentApprovalCanIssueAnAuthorization()throws Exception{
  long id=request(target);var barrier=new CyclicBarrier(2);String token=jwt;
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Integer> attempt=()->{barrier.await(10,TimeUnit.SECONDS);return mvc.perform(post("/admin/password-recoveries/"+id+"/approve").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"identityVerified\":true}")).andReturn().getResponse().getStatus();};
   var a=pool.submit(attempt);var b=pool.submit(attempt);var statuses=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));assertTrue(statuses.contains(200));assertTrue(statuses.contains(409));
  }
 }
 @Test void administratorCanRecoverWithoutChangingRoleOrDisablingOtherAdmin()throws Exception{
  User second=create(UserRole.ADMIN);String previous=tokens.generateToken(second);String code=approve(request(second));reset(code,NEW,NEW,200);
  call("GET","/users",null,previous,401);var login=call("POST","/auth/login",Map.of("email",second.getEmail(),"password",NEW),null,200);String current=json.readTree(login.getContentAsString()).get("token").asText();call("GET","/users",null,current,200);
  assertEquals(UserRole.ADMIN,users.findById(second.getId()).orElseThrow().getRole());assertTrue(users.findById(admin.getId()).orElseThrow().isEnabled());
 }
}
