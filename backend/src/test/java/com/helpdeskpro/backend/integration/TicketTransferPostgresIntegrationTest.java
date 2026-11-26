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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="HELPDESK_DISPOSABLE_TEST_PG_URL",matches=".+")
class TicketTransferPostgresIntegrationTest {
 @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception{
  String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),db="transfer_"+UUID.randomUUID().toString().replace("-","");
  try(var c=DriverManager.getConnection(base,System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
  r.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+db);r.add("spring.datasource.username",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"));r.add("spring.datasource.password",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));
  r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->true);
 }
 @Autowired WebApplicationContext context;@Autowired UserRepository users;@Autowired TicketRepository tickets;@Autowired TicketAuditLogRepository audits;@Autowired TicketCommentRepository comments;@Autowired TokenService tokens;@Autowired PasswordEncoder encoder;@Autowired JdbcTemplate jdbc;
 MockMvc mvc;ObjectMapper json=new ObjectMapper();User admin,admin2,tech,tech2,tech3,requester;static String hash;
 @BeforeEach void company(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();if(hash==null)hash=encoder.encode("Ficticia-Transferencia-2026!");admin=user(UserRole.ADMIN);admin2=user(UserRole.ADMIN);tech=user(UserRole.TECHNICIAN);tech2=user(UserRole.TECHNICIAN);tech3=user(UserRole.TECHNICIAN);requester=user(UserRole.REQUESTER);}
 User user(UserRole role){return users.saveAndFlush(new User("Empresa isolada",UUID.randomUUID()+"@transfer.test",hash,role));}
 Ticket ticket(User owner,TicketStatus status){Ticket t=new Ticket("Transferência "+UUID.randomUUID(),"Descrição preservada",TicketCategory.OUTRO,TicketPriority.MEDIA,requester,null);t.setStatus(status);t.setTechnician(owner);return tickets.saveAndFlush(t);}
 record Result(int status,JsonNode body){}
 Result call(String path,Object body,User actor)throws Exception{var b=patch(path).header("Authorization","Bearer "+tokens.generateToken(actor)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));var r=mvc.perform(b).andReturn().getResponse();return new Result(r.getStatus(),json.readTree(r.getContentAsString()));}
 Map<String,Object> body(User next,Long version,String why){Map<String,Object> b=new HashMap<>();b.put("technicianId",next.getId());if(version!=null)b.put("expectedVersion",version);if(why!=null)b.put("justification",why);return b;}
 Result assign(Ticket t,User next,User actor,String why,int expected)throws Exception{var r=call("/tickets/"+t.getId()+"/technician",body(next,t.getVersion(),why),actor);assertEquals(expected,r.status);return r;}
 List<Result> race(Callable<Result> first,Callable<Result> second)throws Exception{var start=new CyclicBarrier(2);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return first.call();});var b=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return second.call();});return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}}
 @Test void adminAssignsUnownedAndTechnicianClaimsOnlyForSelf()throws Exception{
  Ticket a=ticket(null,TicketStatus.CRIADO);var r=assign(a,tech,admin,null,200);assertEquals("ABERTO",r.body.path("status").asText());assertEquals(1,r.body.path("version").asLong());
  Ticket b=ticket(null,TicketStatus.CRIADO);assign(b,tech2,tech2,null,200);
  assertEquals(tech2.getId(),tickets.findById(b.getId()).orElseThrow().getTechnician().getId());
 }
 @Test void twoTechniciansClaimingHaveOneWinner()throws Exception{
  Ticket t=ticket(null,TicketStatus.CRIADO);String p="/tickets/"+t.getId()+"/technician";
  var r=race(()->call(p,body(tech,0L,null),tech),()->call(p,body(tech2,0L,null),tech2));assertEquals(List.of(200,409),r.stream().map(Result::status).sorted().toList());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE ticket_id=? AND field_name='technician'",Integer.class,t.getId()));
 }
 @Test void administratorTransfersActiveOwnerWithCompleteAudit()throws Exception{
  Ticket t=ticket(tech,TicketStatus.EM_ATENDIMENTO);var before=Instant.now().minusSeconds(1);assign(t,tech2,admin,"  Continuidade do atendimento  ",200);
  var rows=audits.findByTicketIdOrderByChangedAtAsc(t.getId());assertEquals(1,rows.size());var event=rows.getFirst();assertEquals(t.getId(),event.getTicket().getId());assertEquals(admin.getId(),event.getChangedBy().getId());assertEquals(tech.getId().toString(),event.getOldValue());assertEquals(tech2.getId().toString(),event.getNewValue());assertEquals("Continuidade do atendimento",event.getJustification());assertTrue(event.getChangedAt().isAfter(before));assertEquals(TicketStatus.EM_ATENDIMENTO,tickets.findById(t.getId()).orElseThrow().getStatus());
 }
 @Test void transferRequiresReasonIncludingWhitespaceAndLengthLimit()throws Exception{
  Ticket t=ticket(tech,TicketStatus.ABERTO);for(String reason:new String[]{null,"  ","x".repeat(2001)})assign(t,tech2,admin,reason,400);
  assertEquals(tech.getId(),tickets.findById(t.getId()).orElseThrow().getTechnician().getId());assertTrue(audits.findByTicketIdOrderByChangedAtAsc(t.getId()).isEmpty());
 }
 @Test void technicianCannotAssignAnotherOrTransferEitherOwner()throws Exception{
  assign(ticket(null,TicketStatus.CRIADO),tech2,tech,null,403);
  assign(ticket(tech2,TicketStatus.ABERTO),tech,tech,"Assumir indevidamente",403);
  assign(ticket(tech,TicketStatus.ABERTO),tech2,tech,"Transferência indevida",403);
 }
 @Test void requesterCannotChangeOwner()throws Exception{assign(ticket(tech,TicketStatus.ABERTO),tech2,requester,"Indevida",403);}
 @Test void inactiveAndNonTechnicianAreIneligible()throws Exception{
  tech2.setActive(false);users.saveAndFlush(tech2);assign(ticket(null,TicketStatus.CRIADO),tech2,admin,null,400);
  tech3.setRole(UserRole.REQUESTER);users.saveAndFlush(tech3);assign(ticket(tech,TicketStatus.ABERTO),tech3,admin,"Troca",400);
 }
 @Test void adminReplacesInactiveAndFormerTechnicianWithoutDeletingHistory()throws Exception{
  Ticket a=ticket(tech,TicketStatus.EM_ATENDIMENTO);tech.setActive(false);users.saveAndFlush(tech);assign(a,tech2,admin,"Cobertura de ausência",200);
  Ticket b=ticket(tech3,TicketStatus.AGUARDANDO_USUARIO);tech3.setRole(UserRole.REQUESTER);users.saveAndFlush(tech3);assign(b,tech2,admin,"Responsável mudou de função",200);
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE old_value IN (?,?)",Integer.class,tech.getId().toString(),tech3.getId().toString()));assertTrue(users.existsById(tech.getId()));
 }
 @Test void missingAndStaleRevisionAreRejected()throws Exception{
  Ticket t=ticket(tech,TicketStatus.ABERTO);assertEquals(400,call("/tickets/"+t.getId()+"/technician",body(tech2,null,"Sem versão"),admin).status);
  assign(t,tech2,admin,"Primeira operação",200);assign(t,tech3,admin,"Versão desatualizada",409);
 }
 @Test void twoAdministratorsTransferOneRevisionExactlyOnce()throws Exception{
  Ticket t=ticket(tech,TicketStatus.EM_ATENDIMENTO);String p="/tickets/"+t.getId()+"/technician";
  var r=race(()->call(p,body(tech2,0L,"Motivo A"),admin),()->call(p,body(tech3,0L,"Motivo B"),admin2));assertEquals(List.of(200,409),r.stream().map(Result::status).sorted().toList());assertEquals(1,audits.findByTicketIdOrderByChangedAtAsc(t.getId()).size());
 }
 @Test void formerTechnicianCannotModifyAfterTransfer()throws Exception{
  Ticket t=ticket(tech,TicketStatus.EM_ATENDIMENTO);assign(t,tech2,admin,"Nova equipe",200);
  assertEquals(403,call("/tickets/"+t.getId()+"/status",Map.of("status","RESOLVIDO","solution","Tentativa do antigo","expectedVersion",1),tech).status);
  assertEquals(200,call("/tickets/"+t.getId()+"/status",Map.of("status","RESOLVIDO","solution","Correção do responsável","expectedVersion",1),tech2).status);
 }
 @Test void commentsAttachmentSolutionAndPriorityArePreserved()throws Exception{
  Ticket t=ticket(tech,TicketStatus.EM_ATENDIMENTO);t.setSolution("Diagnóstico anterior");t.setAttachmentName("evidencia.png");t.setAttachmentContentType("image/png");t.setAttachmentData(new byte[]{1,2,3});t=tickets.saveAndFlush(t);comments.saveAndFlush(new TicketComment("Histórico mantido",t,requester));
  assign(t,tech2,admin,"Continuidade sem perda",200);Ticket after=tickets.findById(t.getId()).orElseThrow();assertEquals("Diagnóstico anterior",after.getSolution());assertArrayEquals(new byte[]{1,2,3},after.getAttachmentData());assertEquals(TicketPriority.MEDIA,after.getPriority());assertEquals(requester.getId(),after.getRequester().getId());assertEquals(1,comments.findByTicketIdOrderByCreatedAtAsc(t.getId()).size());
 }
 @Test void dashboardAndListsShowNewOwner()throws Exception{
  Ticket t=ticket(tech,TicketStatus.ABERTO);assign(t,tech2,admin,"Fila correta",200);String jwt=tokens.generateToken(admin);
  var list=json.readTree(mvc.perform(get("/tickets").header("Authorization","Bearer "+jwt)).andReturn().getResponse().getContentAsString());JsonNode row=null;for(var item:list)if(item.path("id").asLong()==t.getId())row=item;assertNotNull(row);assertEquals(tech2.getId().longValue(),row.path("technicianId").asLong());assertEquals(1,row.path("version").asLong());
  var dashboard=json.readTree(mvc.perform(get("/dashboard/summary").header("Authorization","Bearer "+jwt)).andReturn().getResponse().getContentAsString());assertTrue(dashboard.path("ticketsByTechnician").findValues("technicianId").stream().anyMatch(id->id.asLong()==tech2.getId()));
 }
 @Test void statusUpdateConcurrentWithTransferHasOneWinner()throws Exception{
  Ticket t=ticket(tech,TicketStatus.EM_ATENDIMENTO);var r=race(()->call("/tickets/"+t.getId()+"/technician",body(tech2,0L,"Transferência simultânea"),admin),()->call("/tickets/"+t.getId()+"/status",Map.of("status","AGUARDANDO_USUARIO","expectedVersion",0),tech));assertEquals(List.of(200,409),r.stream().map(Result::status).sorted().toList());assertEquals(1,audits.findByTicketIdOrderByChangedAtAsc(t.getId()).size());
 }
 @Test void failedAuditRollsBackOwnerRevisionAndReason()throws Exception{
  Ticket t=ticket(tech,TicketStatus.EM_ATENDIMENTO);
  jdbc.execute("CREATE FUNCTION fail_transfer_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.ticket_id="+t.getId()+" THEN RAISE EXCEPTION 'isolated audit outage'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER fail_transfer_audit BEFORE INSERT ON ticket_audit_logs FOR EACH ROW EXECUTE FUNCTION fail_transfer_audit()");
  try{assign(t,tech2,admin,"Não deve persistir",500);}finally{jdbc.execute("DROP TRIGGER fail_transfer_audit ON ticket_audit_logs");jdbc.execute("DROP FUNCTION fail_transfer_audit()");}
  Ticket after=tickets.findById(t.getId()).orElseThrow();assertEquals(tech.getId(),after.getTechnician().getId());assertEquals(0,after.getVersion());assertTrue(audits.findByTicketIdOrderByChangedAtAsc(t.getId()).isEmpty());
 }
 @ParameterizedTest @EnumSource(value=TicketStatus.class,names={"RESOLVIDO","FECHADO","CANCELADO"})
 void terminalStatesRejectInitialAssignmentAndTransfer(TicketStatus state)throws Exception{assign(ticket(null,state),tech,admin,null,400);assign(ticket(tech,state),tech2,admin,"Não contornar encerramento",400);assign(ticket(null,state),tech,tech,null,400);}
 @Test void sameOwnerIsIdempotentWithoutFalseTransferAudit()throws Exception{Ticket t=ticket(tech,TicketStatus.ABERTO);assign(t,tech,admin,null,200);assertTrue(audits.findByTicketIdOrderByChangedAtAsc(t.getId()).isEmpty());assertEquals(0,tickets.findById(t.getId()).orElseThrow().getVersion());}
 @Test void destinationDeactivatedWhileAssignmentWaitsIsRejected()throws Exception{eligibilityRace(false);}
 @Test void destinationLosesRoleWhileTransferWaitsIsRejected()throws Exception{eligibilityRace(true);}
 void eligibilityRace(boolean role)throws Exception{
  Ticket t=ticket(role?tech:null,TicketStatus.ABERTO);
  try(var connection=jdbc.getDataSource().getConnection();var pool=Executors.newSingleThreadExecutor()){
   connection.setAutoCommit(false);try(var p=connection.prepareStatement(role?"UPDATE users SET role='REQUESTER',session_version=session_version+1,edit_version=edit_version+1 WHERE id=?":"UPDATE users SET active=false,session_version=session_version+1,edit_version=edit_version+1 WHERE id=?")){p.setLong(1,tech2.getId());p.executeUpdate();}
   var f=pool.submit(()->assign(t,tech2,admin,"Mudança durante a operação",400));boolean blocked=false;
   for(int i=0;i<200;i++){if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class)>0){blocked=true;break;}Thread.sleep(10);}
   assertTrue(blocked,"Assignment must wait for the eligibility row lock");connection.commit();f.get(15,TimeUnit.SECONDS);
  }
  assertEquals(t.getTechnician()==null?null:tech.getId(),tickets.findById(t.getId()).orElseThrow().getTechnician()==null?null:tickets.findById(t.getId()).orElseThrow().getTechnician().getId());assertTrue(audits.findByTicketIdOrderByChangedAtAsc(t.getId()).isEmpty());
 }
}
