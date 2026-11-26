package com.helpdeskpro.backend.integration;

import com.fasterxml.jackson.databind.*;
import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.domain.entities.*;
import com.helpdeskpro.backend.domain.enums.*;
import com.helpdeskpro.backend.repositories.*;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@Import(TicketSlaPostgresIntegrationTest.TimeConfig.class)
@EnabledIfEnvironmentVariable(named="HELPDESK_DISPOSABLE_TEST_PG_URL", matches=".+")
class TicketSlaPostgresIntegrationTest {
    static final Instant START=Instant.parse("2026-01-01T12:00:00Z");
    static final MutableClock TIME=new MutableClock();
    static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(START);
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
        public Instant instant(){return value.get();}
        void at(Instant instant){value.set(instant);}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary Clock slaTestClock(){return TIME;}}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception {
        String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),db="sla_"+UUID.randomUUID().toString().replace("-","");
        try(var c=connect(base);var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
        r.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+db);
        r.add("spring.datasource.username",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"));
        r.add("spring.datasource.password",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));
        r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->true);
    }
    static Connection connect(String url)throws Exception{return DriverManager.getConnection(url,System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));}
    @Autowired WebApplicationContext context;ObjectMapper json=new ObjectMapper().findAndRegisterModules();@Autowired TicketRepository tickets;
    @Autowired UserRepository users;@Autowired TicketCommentRepository comments;@Autowired TicketAuditLogRepository audits;
    @Autowired TokenService tokens;@Autowired PasswordEncoder encoder;@Autowired JdbcTemplate jdbc;
    MockMvc mvc;User admin,tech,requester,stranger;static String hash;
    @BeforeEach void setup(){TIME.at(START);mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();if(hash==null)hash=encoder.encode(UUID.randomUUID().toString());admin=user(UserRole.ADMIN);tech=user(UserRole.TECHNICIAN);requester=user(UserRole.REQUESTER);stranger=user(UserRole.REQUESTER);}
    User user(UserRole role){return users.saveAndFlush(new User("Homologação SLA",UUID.randomUUID()+"@sla.test",hash,role));}
    Ticket ticket(TicketPriority priority,TicketStatus status){var t=new Ticket("SLA "+UUID.randomUUID(),"Descrição preservada",TicketCategory.DUVIDA,priority,requester,null);t.setCreatedAt(START);t.setStatus(status);t.setTechnician(tech);return tickets.saveAndFlush(t);}
    record Result(int code,JsonNode body){}
    Result call(String method,String path,Object body,User actor)throws Exception{
        var req=switch(method){case "POST"->post(path);case "PATCH"->patch(path);default->get(path);};req.header("Authorization","Bearer "+tokens.generateToken(actor));
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response=mvc.perform(req).andReturn().getResponse();return new Result(response.getStatus(),json.readTree(response.getContentAsString()));
    }
    JsonNode detail(Ticket t,User actor)throws Exception{var r=call("GET","/tickets/"+t.getId(),null,actor);assertEquals(200,r.code);return r.body;}
    Result state(Ticket t,String status,User actor)throws Exception{return call("PATCH","/tickets/"+t.getId()+"/status",Map.of("status",status,"solution","Solução técnica validada","expectedVersion",t.getVersion()),actor);}
    void assertSla(JsonNode value,String status,boolean breached,long delay){assertEquals(status,value.path("slaStatus").asText());assertEquals(breached,value.path("slaBreached").asBoolean());assertEquals(delay,value.path("slaDelaySeconds").asLong());}
    @ParameterizedTest @EnumSource(TicketPriority.class)
    void creationAndContinuousDeadlineUseClockAndPriority(TicketPriority priority)throws Exception{
        String category=switch(priority){case ALTA->"INCIDENTE_SISTEMA";case MEDIA->"SOLICITACAO_ACESSO";case BAIXA->"DUVIDA";};
        var r=call("POST","/tickets",Map.of("title","Prazo "+UUID.randomUUID(),"description","Solicitação de homologação","category",category,"priority","ALTA"),requester);assertEquals(201,r.code);
        assertEquals(priority.name(),r.body.path("priority").asText());assertEquals(START,Instant.parse(r.body.path("createdAt").asText()));
        assertEquals(START.plusSeconds(priority.getSlaHours()*3600),Instant.parse(r.body.path("slaDeadline").asText()));assertSla(r.body,"IN_PROGRESS_ON_TIME",false,0);
    }
    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void exactDeadlineBoundaryAcrossDetailListAndDashboard(int microseconds)throws Exception{
        Ticket t=ticket(TicketPriority.ALTA,TicketStatus.EM_ATENDIMENTO);TIME.at(START.plusSeconds(14400).plusNanos(microseconds*1000L));
        String expected=microseconds>0?"IN_PROGRESS_OVERDUE":"IN_PROGRESS_ON_TIME";assertSla(detail(t,admin),expected,microseconds>0,microseconds>0?1:0);
        var list=call("GET","/tickets",null,admin).body;Map<String,Long> counts=new HashMap<>();
        for(var item:list){counts.merge(item.path("slaStatus").asText(),1L,Long::sum);if(item.path("id").asLong()==t.getId())assertSla(item,expected,microseconds>0,microseconds>0?1:0);}
        var summary=call("GET","/dashboard/summary",null,admin);assertEquals(200,summary.code);for(var entry:counts.entrySet())assertEquals(entry.getValue().longValue(),summary.body.path("ticketsBySla").path(entry.getKey()).asLong());
    }
    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void resolutionAndLaterClosingFreezeOutcome(int microseconds)throws Exception{
        Ticket t=ticket(TicketPriority.ALTA,TicketStatus.EM_ATENDIMENTO);Instant deadline=START.plusSeconds(14400),resolved=deadline.plusNanos(microseconds*1000L);TIME.at(resolved);
        var r=state(t,"RESOLVIDO",tech);assertEquals(200,r.code);String expected=microseconds>0?"RESOLVED_OVERDUE":"RESOLVED_ON_TIME";assertSla(r.body,expected,false,microseconds>0?1:0);
        Ticket stored=tickets.findById(t.getId()).orElseThrow();assertEquals(resolved,stored.getResolvedAt());assertEquals(deadline,stored.getSlaResolutionDeadline());
        assertEquals(resolved,audits.findByTicketIdOrderByChangedAtAsc(t.getId()).stream().filter(a->a.getFieldName().equals("status")).findFirst().orElseThrow().getChangedAt());
        TIME.at(deadline.plus(Duration.ofDays(30)));var closed=state(stored,"FECHADO",admin);assertEquals(200,closed.code);assertSla(closed.body,expected,false,microseconds>0?1:0);assertEquals(resolved,Instant.parse(closed.body.path("resolvedAt").asText()));
        for(var item:call("GET","/tickets",null,admin).body)if(item.path("id").asLong()==t.getId())assertEquals(resolved,Instant.parse(item.path("resolvedAt").asText()));
    }
    @Test void cancellationHasNoComplianceOutcome()throws Exception{var t=ticket(TicketPriority.ALTA,TicketStatus.ABERTO);TIME.at(START.plus(Duration.ofDays(10)));var r=call("PATCH","/tickets/"+t.getId()+"/status",Map.of("status","CANCELADO","expectedVersion",0),requester);assertEquals(200,r.code);assertEquals("CANCELLED",r.body.path("slaStatus").asText());assertTrue(r.body.path("slaDelaySeconds").isNull());assertTrue(r.body.path("resolvedAt").isNull());assertFalse(r.body.path("slaBreached").asBoolean());}
    @Test void awaitingRequesterDoesNotPauseClock()throws Exception{var t=ticket(TicketPriority.MEDIA,TicketStatus.EM_ATENDIMENTO);assertEquals(200,state(t,"AGUARDANDO_USUARIO",tech).code);TIME.at(START.plus(Duration.ofHours(25)));assertSla(detail(t,tech),"IN_PROGRESS_OVERDUE",true,3600);var r=state(tickets.findById(t.getId()).orElseThrow(),"RESOLVIDO",tech);assertEquals(200,r.code);assertSla(r.body,"RESOLVED_OVERDUE",false,3600);}
    @ParameterizedTest @EnumSource(value=TicketStatus.class,names={"RESOLVIDO","FECHADO"})
    void legacyAuditAloneDoesNotInventHistoricalDeadline(TicketStatus status)throws Exception{var t=ticket(TicketPriority.MEDIA,status);var event=audits.saveAndFlush(new TicketAuditLog(t,admin,"status","EM_ATENDIMENTO","RESOLVIDO"));var before=jdbc.queryForObject("SELECT to_jsonb(t)::text FROM tickets t WHERE id=?",String.class,t.getId());TIME.at(START.plus(Duration.ofDays(500)));var r=detail(t,admin);assertEquals("UNKNOWN",r.path("slaStatus").asText());assertTrue(r.path("resolvedAt").isNull());assertEquals(before,jdbc.queryForObject("SELECT to_jsonb(t)::text FROM tickets t WHERE id=?",String.class,t.getId()));assertTrue(audits.existsById(event.getId()));if(status==TicketStatus.RESOLVIDO){assertEquals(200,state(t,"FECHADO",admin).code);assertNull(tickets.findById(t.getId()).orElseThrow().getResolvedAt());}}
    @Test void sameStateAndLaterPriorityCannotRewriteRecordedResolution()throws Exception{var t=ticket(TicketPriority.ALTA,TicketStatus.EM_ATENDIMENTO);TIME.at(START.plus(Duration.ofHours(5)));assertEquals(200,state(t,"RESOLVIDO",tech).code);var stored=tickets.findById(t.getId()).orElseThrow();Instant at=stored.getResolvedAt(),deadline=stored.getSlaResolutionDeadline();TIME.at(START.plus(Duration.ofDays(1)));assertEquals(200,state(stored,"RESOLVIDO",tech).code);jdbc.update("UPDATE tickets SET priority='BAIXA' WHERE id=?",t.getId());assertSla(detail(t,admin),"RESOLVED_OVERDUE",false,3600);assertEquals(at,tickets.findById(t.getId()).orElseThrow().getResolvedAt());assertEquals(deadline,Instant.parse(detail(t,admin).path("slaDeadline").asText()));}
    @Test void utcSerializationAndTimeZoneAreIndependent()throws Exception{var t=ticket(TicketPriority.ALTA,TicketStatus.EM_ATENDIMENTO);Instant at=START.plusSeconds(14401);TIME.at(at);assertEquals(at,TIME.withZone(ZoneId.of("Asia/Tokyo")).instant());var r=state(t,"RESOLVIDO",tech);assertEquals(200,r.code);assertEquals("2026-01-01T16:00:01Z",r.body.path("resolvedAt").asText());assertEquals("2026-01-01T16:00:00Z",r.body.path("slaDeadline").asText());}
    List<Result> race(Callable<Result> first,Callable<Result> second)throws Exception{var barrier=new CyclicBarrier(2);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return first.call();});var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return second.call();});return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}}
    @Test void concurrentResolutionsPersistOneTimestampAndOneStatusAudit()throws Exception{var t=ticket(TicketPriority.ALTA,TicketStatus.EM_ATENDIMENTO);TIME.at(START.plus(Duration.ofHours(5)));var results=race(()->state(t,"RESOLVIDO",admin),()->state(t,"RESOLVIDO",tech));assertEquals(List.of(200,409),results.stream().map(Result::code).sorted().toList());assertEquals(TIME.instant(),tickets.findById(t.getId()).orElseThrow().getResolvedAt());assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE ticket_id=? AND field_name='status'",Integer.class,t.getId()));}
    @Test void resolutionRacingWaitingStatusIsAtomic()throws Exception{var t=ticket(TicketPriority.MEDIA,TicketStatus.EM_ATENDIMENTO);TIME.at(START.plus(Duration.ofHours(25)));var results=race(()->state(t,"RESOLVIDO",tech),()->state(t,"AGUARDANDO_USUARIO",admin));assertEquals(List.of(200,409),results.stream().map(Result::code).sorted().toList());var stored=tickets.findById(t.getId()).orElseThrow();assertEquals(stored.getStatus()==TicketStatus.RESOLVIDO,stored.getResolvedAt()!=null);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE ticket_id=? AND field_name='status'",Integer.class,t.getId()));}
    @Test void failedAuditRollsBackSlaSnapshotAndState()throws Exception{var t=ticket(TicketPriority.MEDIA,TicketStatus.EM_ATENDIMENTO);jdbc.execute("CREATE FUNCTION sla_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.ticket_id="+t.getId()+" THEN RAISE EXCEPTION 'isolated audit outage'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER sla_audit_failure BEFORE INSERT ON ticket_audit_logs FOR EACH ROW EXECUTE FUNCTION sla_audit_failure()");try{assertEquals(500,state(t,"RESOLVIDO",tech).code);}finally{jdbc.execute("DROP TRIGGER sla_audit_failure ON ticket_audit_logs");jdbc.execute("DROP FUNCTION sla_audit_failure()");}var stored=tickets.findById(t.getId()).orElseThrow();assertNull(stored.getResolvedAt());assertNull(stored.getSlaResolutionDeadline());assertEquals(TicketStatus.EM_ATENDIMENTO,stored.getStatus());assertEquals(0,stored.getVersion());assertEquals(0,audits.findByTicketIdOrderByChangedAtAsc(t.getId()).size());}
    @ParameterizedTest @EnumSource(UserRole.class)
    void rolePermissionsAndOwnershipRemainEnforced(UserRole role)throws Exception{var t=ticket(TicketPriority.MEDIA,TicketStatus.EM_ATENDIMENTO);User actor=switch(role){case ADMIN->admin;case TECHNICIAN->tech;case REQUESTER->requester;};assertEquals(200,call("GET","/tickets/"+t.getId(),null,actor).code);assertEquals(role==UserRole.REQUESTER?403:200,state(t,"RESOLVIDO",actor).code);assertEquals(403,call("GET","/tickets/"+t.getId(),null,stranger).code);assertEquals(role==UserRole.REQUESTER?403:200,call("GET","/dashboard/summary",null,actor).code);}
    @Test void commentsAttachmentAndEarlierAuditSurviveResolution()throws Exception{var t=ticket(TicketPriority.MEDIA,TicketStatus.EM_ATENDIMENTO);t.setAttachmentName("evidencia.png");t.setAttachmentContentType("image/png");t.setAttachmentData(new byte[]{1,2,3});t=tickets.saveAndFlush(t);comments.saveAndFlush(new TicketComment("Comentário histórico",t,requester));var event=audits.saveAndFlush(new TicketAuditLog(t,admin,"technician",null,tech.getId().toString()));assertEquals(200,state(t,"RESOLVIDO",tech).code);var stored=tickets.findById(t.getId()).orElseThrow();assertArrayEquals(new byte[]{1,2,3},stored.getAttachmentData());assertEquals(requester.getId(),stored.getRequester().getId());assertEquals(1,comments.findByTicketIdOrderByCreatedAtAsc(t.getId()).size());assertTrue(audits.existsById(event.getId()));}
    @Test void postgresRejectsPartialSnapshot(){var t=ticket(TicketPriority.MEDIA,TicketStatus.EM_ATENDIMENTO);assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE tickets SET resolved_at=created_at WHERE id=?",t.getId()));assertNull(tickets.findById(t.getId()).orElseThrow().getResolvedAt());}
    @Test void migrationFromV8PreservesEveryExistingColumnAndIsIdempotent()throws Exception{
        String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),db="sla_migration_"+UUID.randomUUID().toString().replace("-","");try(var c=connect(base);var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}String url=base.substring(0,base.lastIndexOf('/')+1)+db;
        String user=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),password=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD");org.flywaydb.core.Flyway.configure().dataSource(url,user,password).target("8").load().migrate();
        try(var c=connect(url);var s=c.createStatement()){
            s.execute("INSERT INTO users(id,name,email,password,role) VALUES (1,'Admin um','a1@migration.test','not-a-real-credential','ADMIN'),(2,'Admin dois','a2@migration.test','not-a-real-credential','ADMIN'),(3,'Solicitante','r@migration.test','not-a-real-credential','REQUESTER')");
            s.execute("INSERT INTO tickets(id,title,description,status,category,priority,requester_id,created_at,solution,attachment_name,attachment_content_type,attachment_data) VALUES (1,'Histórico','Descrição','RESOLVIDO','DUVIDA','BAIXA',3,'2026-01-01T12:00:00Z','Solução antiga','evidencia.png','image/png',decode('010203','hex'))");
            s.execute("INSERT INTO ticket_comments(content,ticket_id,author_id,created_at) VALUES ('Histórico',1,3,'2026-01-01T13:00:00Z')");
            s.execute("INSERT INTO ticket_audit_logs(ticket_id,changed_by_id,field_name,old_value,new_value,changed_at) VALUES (1,1,'status','EM_ATENDIMENTO','RESOLVIDO','2026-01-02T12:00:00Z')");
            List<String> before=fingerprints(c);var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,user,password).target("9").load();assertEquals(1,flyway.migrate().migrationsExecuted);assertEquals(before,fingerprints(c));assertEquals(0,flyway.migrate().migrationsExecuted);assertEquals(before,fingerprints(c));
            try(var r=s.executeQuery("SELECT count(*) FROM tickets WHERE resolved_at IS NULL AND sla_resolution_deadline IS NULL")){r.next();assertEquals(1,r.getInt(1));}
            try(var r=s.executeQuery("SELECT count(*) FROM users WHERE role='ADMIN' AND active")){r.next();assertEquals(2,r.getInt(1));}
        }
    }
    List<String> fingerprints(Connection c)throws Exception{List<String> result=new ArrayList<>();for(String table:List.of("users","tickets","ticket_comments","ticket_audit_logs")){try(var s=c.createStatement();var r=s.executeQuery("SELECT md5(COALESCE(string_agg((to_jsonb(t)-'resolved_at'-'sla_resolution_deadline')::text,',' ORDER BY id),'')) FROM "+table+" t")){r.next();result.add(r.getString(1));}}return result;}
}