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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="HELPDESK_DISPOSABLE_TEST_PG_URL",matches=".+")
class TicketOperationsPostgresIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception {
        String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),db="operations_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(base,System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
        r.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+db);
        r.add("spring.datasource.username",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"));r.add("spring.datasource.password",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));
        r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->true);
    }
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired TicketRepository tickets;
    @Autowired AssetRepository assets;
    @Autowired TicketCommentRepository comments;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;ObjectMapper json=new ObjectMapper();User admin,tech,otherTech,requester,otherRequester;
    String password="Operacao-Isolada-2026!";String hash;
    @BeforeAll void company()throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();hash=encoder.encode(password);
        admin=user(UserRole.ADMIN);tech=user(UserRole.TECHNICIAN);otherTech=user(UserRole.TECHNICIAN);requester=user(UserRole.REQUESTER);otherRequester=user(UserRole.REQUESTER);
        preparePreview();
    }
    User user(UserRole role){return users.saveAndFlush(new User("Empresa de homologação",UUID.randomUUID()+"@operacao.test",hash,role));}
    Ticket ticket(User owner,TicketStatus status,User technician){Ticket t=new Ticket("Homologação "+UUID.randomUUID(),"Descrição operacional",TicketCategory.DUVIDA,TicketPriority.BAIXA,owner,null);t.setStatus(status);t.setTechnician(technician);return tickets.saveAndFlush(t);}
    record Result(int status,JsonNode body){}
    Result call(String method,String path,Object body,User actor)throws Exception {
        if (method.equals("PATCH") && path.endsWith("/technician") && body instanceof Map<?,?> original) {
            var revised = new HashMap<String,Object>(); original.forEach((k,v)->revised.put(k.toString(),v));
            revised.putIfAbsent("expectedVersion",tickets.findById(Long.valueOf(path.split("/")[2])).orElseThrow().getVersion());
            body = revised;
        }
        var req=switch(method){case "POST"->post(path);case "PATCH"->patch(path);default->get(path);};
        if(actor!=null)req.header("Authorization","Bearer "+tokens.generateToken(actor));
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response=mvc.perform(req).andReturn().getResponse();String content=response.getContentAsString();
        return new Result(response.getStatus(),content.isEmpty()?json.createObjectNode():json.readTree(content));
    }
    JsonNode ok(String method,String path,Object body,User actor,int expected)throws Exception {var r=call(method,path,body,actor);assertEquals(expected,r.status,path);return r.body;}
    Map<String,Object> change(TicketStatus status,String solution,Long version){Map<String,Object> p=new HashMap<>();p.put("status",status.name());if(solution!=null)p.put("solution",solution);if(version!=null)p.put("expectedVersion",version);return p;}
    long version(long id)throws Exception{return ok("GET","/tickets/"+id,null,admin,200).path("version").asLong();}
    void assign(long id,User target)throws Exception {ok("PATCH","/tickets/"+id+"/technician",Map.of("technicianId",target.getId(),"expectedVersion",version(id)),admin,200);}
    void state(long id,TicketStatus status,User actor)throws Exception {ok("PATCH","/tickets/"+id+"/status",change(status,"Solução registrada",version(id)),actor,200);}
    static Stream<Arguments> transitions(){
        var edges=Set.of("CRIADO:ABERTO","CRIADO:CANCELADO","ABERTO:EM_ATENDIMENTO","ABERTO:CANCELADO","EM_ATENDIMENTO:AGUARDANDO_USUARIO","EM_ATENDIMENTO:RESOLVIDO","AGUARDANDO_USUARIO:EM_ATENDIMENTO","AGUARDANDO_USUARIO:RESOLVIDO","RESOLVIDO:FECHADO");
        return Arrays.stream(UserRole.values()).flatMap(role->Arrays.stream(TicketStatus.values()).flatMap(from->Arrays.stream(TicketStatus.values()).map(to->{
            boolean allowed=from==to||edges.contains(from+":"+to);
            int expected=role==UserRole.REQUESTER&&to!=TicketStatus.CANCELADO?403:allowed?200:400;
            return Arguments.of(role,from,to,expected);
        })));
    }
    @ParameterizedTest(name="{0}: {1} -> {2}: HTTP {3}") @MethodSource("transitions")
    void stateAndProfileMatrix(UserRole role,TicketStatus from,TicketStatus to,int expected)throws Exception {
        Ticket t=ticket(requester,from,tech);User actor=role==UserRole.ADMIN?admin:role==UserRole.TECHNICIAN?tech:requester;
        var body=change(to,role==UserRole.REQUESTER?null:"Solução válida",version(t.getId()));
        ok("PATCH","/tickets/"+t.getId()+"/status",body,actor,expected);
        assertEquals(expected==200?to:from,tickets.findById(t.getId()).orElseThrow().getStatus());
    }
    @Test void requesterCannotInjectSolutionWithCancellation()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CRIADO,null);
        ok("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.CANCELADO,"Solução forjada",version(t.getId())),requester,403);
        assertNull(tickets.findById(t.getId()).orElseThrow().getSolution());assertEquals(TicketStatus.CRIADO,tickets.findById(t.getId()).orElseThrow().getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE ticket_id=?",Integer.class,t.getId()));
    }
    @Test void staleStatusCannotOverwriteSolution()throws Exception {
        Ticket t=ticket(requester,TicketStatus.EM_ATENDIMENTO,tech);long v=version(t.getId());
        ok("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.EM_ATENDIMENTO,"Solução do técnico",v),tech,200);
        ok("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.EM_ATENDIMENTO,"Solução desatualizada",v),admin,409);
        assertEquals("Solução do técnico",tickets.findById(t.getId()).orElseThrow().getSolution());
    }
    @Test void staleAssignmentDoesNotOverrideStatusUpdate()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CRIADO,null);long v=version(t.getId());state(t.getId(),TicketStatus.ABERTO,admin);
        ok("PATCH","/tickets/"+t.getId()+"/technician",Map.of("technicianId",tech.getId(),"expectedVersion",v),admin,409);
        assertNull(tickets.findById(t.getId()).orElseThrow().getTechnician());
    }
    List<Result> race(Callable<Result> first,Callable<Result> second)throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return first.call();});
            var b=pool.submit(()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));return second.call();});
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
    void oneWinner(List<Result> results){assertEquals(List.of(200,409),results.stream().map(Result::status).sorted().toList());}
    @Test void twoTechniciansClaimOneTicket()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CRIADO,null);long v=version(t.getId());String path="/tickets/"+t.getId()+"/technician";
        var r=race(()->call("PATCH",path,Map.of("technicianId",tech.getId(),"expectedVersion",v),tech),()->call("PATCH",path,Map.of("technicianId",otherTech.getId(),"expectedVersion",v),otherTech));oneWinner(r);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE ticket_id=? AND field_name='technician'",Integer.class,t.getId()));
    }
    @Test void administratorAndTechnicianCannotSilentlyOverwriteEachOther()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CRIADO,null);long v=version(t.getId());
        oneWinner(race(()->call("PATCH","/tickets/"+t.getId()+"/technician",Map.of("technicianId",tech.getId(),"expectedVersion",v),tech),()->call("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.ABERTO,null,v),admin)));
    }
    @Test void concurrentStatusChangesHaveOneWinnerAndOneAudit()throws Exception {
        Ticket t=ticket(requester,TicketStatus.EM_ATENDIMENTO,tech);long v=version(t.getId());String path="/tickets/"+t.getId()+"/status";
        oneWinner(race(()->call("PATCH",path,change(TicketStatus.AGUARDANDO_USUARIO,null,v),tech),()->call("PATCH",path,change(TicketStatus.RESOLVIDO,"Solução concorrente",v),admin)));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE ticket_id=? AND field_name='status'",Integer.class,t.getId()));
    }
    @Test void concurrentCancelAndStartCannotProduceInvalidState()throws Exception {
        Ticket t=ticket(requester,TicketStatus.ABERTO,tech);long v=version(t.getId());String path="/tickets/"+t.getId()+"/status";
        oneWinner(race(()->call("PATCH",path,change(TicketStatus.CANCELADO,null,v),requester),()->call("PATCH",path,change(TicketStatus.EM_ATENDIMENTO,null,v),tech)));
        assertTrue(Set.of(TicketStatus.CANCELADO,TicketStatus.EM_ATENDIMENTO).contains(tickets.findById(t.getId()).orElseThrow().getStatus()));
    }
    @Test void concurrentEquivalentIncidentsCreateOnlyOneTicket()throws Exception {
        User owner=user(UserRole.REQUESTER);var body=Map.of("title","Falha simultânea","description","Incidente equivalente","category","INCIDENTE_SISTEMA");
        var r=race(()->call("POST","/tickets",body,owner),()->call("POST","/tickets",body,owner));
        assertEquals(List.of(201,400),r.stream().map(Result::status).sorted().toList());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM tickets WHERE requester_id=?",Integer.class,owner.getId()));
    }
    @Test void commentsDuringStateChangePreserveAuthorUtcAndHistory()throws Exception {
        Ticket t=ticket(requester,TicketStatus.EM_ATENDIMENTO,tech);long v=version(t.getId());Instant before=Instant.now().minusSeconds(1);
        var r=race(()->call("POST","/tickets/"+t.getId()+"/comments",Map.of("content","Informação durante atendimento"),requester),()->call("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.RESOLVIDO,"Correção concluída",v),tech));
        assertEquals(List.of(200,201),r.stream().map(Result::status).sorted().toList());
        JsonNode rows=ok("GET","/tickets/"+t.getId()+"/comments",null,requester,200);assertEquals(1,rows.size());assertEquals(requester.getId().longValue(),rows.get(0).path("authorId").asLong());
        Instant time=OffsetDateTime.parse(rows.get(0).path("createdAt").asText()).toInstant();assertTrue(time.isAfter(before));assertTrue(time.isBefore(Instant.now().plusSeconds(1)));
        assertEquals(TicketStatus.RESOLVIDO,tickets.findById(t.getId()).orElseThrow().getStatus());
    }
    @Test void persistenceFailureRollsBackStateVersionAndAudit()throws Exception {
        Ticket t=ticket(requester,TicketStatus.ABERTO,tech);long v=version(t.getId());
        jdbc.execute("CREATE FUNCTION fail_operational_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.ticket_id="+t.getId()+" THEN RAISE EXCEPTION 'Isolated audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_operational_audit BEFORE INSERT ON ticket_audit_logs FOR EACH ROW EXECUTE FUNCTION fail_operational_audit()");
        try{ok("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.EM_ATENDIMENTO,null,v),tech,500);}finally{jdbc.execute("DROP TRIGGER fail_operational_audit ON ticket_audit_logs");jdbc.execute("DROP FUNCTION fail_operational_audit()");}
        assertEquals(TicketStatus.ABERTO,tickets.findById(t.getId()).orElseThrow().getStatus());assertEquals(v,version(t.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ticket_audit_logs WHERE ticket_id=?",Integer.class,t.getId()));
    }
    @Test void privacyForOtherRequesterAndUnassignedTechnician()throws Exception {
        Ticket t=ticket(requester,TicketStatus.ABERTO,tech);
        for(String suffix:List.of("","/comments","/attachment"))ok("GET","/tickets/"+t.getId()+suffix,null,otherRequester,403);
        ok("GET","/tickets/requester/"+requester.getId(),null,otherRequester,403);
        ok("POST","/tickets/"+t.getId()+"/comments",Map.of("content","Intrusão"),otherRequester,403);
        ok("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.CANCELADO,null,version(t.getId())),otherRequester,403);
        ok("PATCH","/tickets/"+t.getId()+"/technician",Map.of("technicianId",otherTech.getId()),requester,403);
        ok("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.EM_ATENDIMENTO,null,version(t.getId())),otherTech,403);
        ok("GET","/users",null,tech,403);ok("GET","/admin/password-recoveries",null,tech,403);
        for(User actor:List.of(admin,tech,requester)){String text=ok("GET","/tickets/"+t.getId(),null,actor,200).toString();assertFalse(text.contains("password"));assertFalse(text.contains(hash));}
    }
    @Test void inactiveAndRevokedSessionsRemainDenied()throws Exception {
        User disabled=user(UserRole.REQUESTER);String jwt=tokens.generateToken(disabled);disabled.setActive(false);users.saveAndFlush(disabled);
        assertEquals(401,mvc.perform(get("/tickets").header("Authorization","Bearer "+jwt)).andReturn().getResponse().getStatus());
        ok("POST","/auth/login",Map.of("email",disabled.getEmail(),"password",password),null,401);
        User revoked=user(UserRole.TECHNICIAN);String previous=tokens.generateToken(revoked);users.revokeSessionsById(revoked.getId());
        assertEquals(401,mvc.perform(get("/tickets").header("Authorization","Bearer "+previous)).andReturn().getResponse().getStatus());
    }
    @Test void categoriesEquipmentPriorityAndMassAssignmentAreValidated()throws Exception {
        for(TicketCategory category:TicketCategory.values()){
            User owner=user(UserRole.REQUESTER);Asset asset=assets.saveAndFlush(new Asset(null,"OP-"+UUID.randomUUID(),"Notebook","Homologação",AssetStatus.IN_USE));
            Map<String,Object> p=new HashMap<>(Map.of("title","Abertura válida","description","Detalhes","category",category.name(),"requesterId",otherRequester.getId(),"status","FECHADO","priority","ALTA","solution","Ignorada","technicianId",tech.getId()));
            if(category==TicketCategory.INCIDENTE_EQUIPAMENTO)p.put("assetId",asset.getId());
            var created=ok("POST","/tickets",p,owner,201);assertEquals(owner.getId().longValue(),created.path("requesterId").asLong());assertEquals("CRIADO",created.path("status").asText());assertTrue(created.path("technicianId").isNull());assertTrue(created.path("solution").isNull());
            String expected=category==TicketCategory.INCIDENTE_SISTEMA?"ALTA":category==TicketCategory.DUVIDA?"BAIXA":"MEDIA";assertEquals(expected,created.path("priority").asText());
        }
        ok("POST","/tickets",Map.of("title","Equipamento","description","Sem ativo","category","INCIDENTE_EQUIPAMENTO"),requester,400);
        Asset inactive=assets.saveAndFlush(new Asset(null,"OFF-"+UUID.randomUUID(),"Monitor","Inativo",AssetStatus.INACTIVE));
        ok("POST","/tickets",Map.of("title","Equipamento","description","Ativo inativo","category","INCIDENTE_EQUIPAMENTO","assetId",inactive.getId()),requester,400);
        ok("POST","/tickets",Map.of("title"," ","description"," ","category","DUVIDA"),requester,400);
    }
    @Test void fullLifecycleDashboardCommentsAndAttachment()throws Exception {
        User owner=user(UserRole.REQUESTER);var created=ok("POST","/tickets",Map.of("title","Fluxo completo","description","Atendimento de empresa","category","DUVIDA"),owner,201);long id=created.path("id").asLong();
        var login=ok("POST","/auth/login",Map.of("email",owner.getEmail(),"password",password),null,200);assertTrue(login.has("token"));
        assign(id,tech);state(id,TicketStatus.EM_ATENDIMENTO,tech);state(id,TicketStatus.AGUARDANDO_USUARIO,tech);
        ok("POST","/tickets/"+id+"/comments",Map.of("content","Informações do solicitante"),owner,201);
        state(id,TicketStatus.EM_ATENDIMENTO,tech);state(id,TicketStatus.RESOLVIDO,tech);state(id,TicketStatus.FECHADO,tech);
        assertEquals(1,ok("GET","/tickets/"+id+"/comments",null,owner,200).size());
        // Existing policy permits comments after closure; do not invent a restriction.
        ok("POST","/tickets/"+id+"/comments",Map.of("content","Agradecimento após encerramento"),owner,201);
        ok("POST","/tickets/"+id+"/comments",Map.of("content","  "),owner,400);
        var file=new MockMultipartFile("file","imagem.png","image/png",com.helpdeskpro.backend.TestImages.png());
        assertEquals(200,mvc.perform(multipart("/tickets/"+id+"/attachment").file(file).header("Authorization","Bearer "+tokens.generateToken(owner))).andReturn().getResponse().getStatus());
        var download=mvc.perform(get("/tickets/"+id+"/attachment").header("Authorization","Bearer "+tokens.generateToken(owner))).andReturn().getResponse();assertEquals(200,download.getStatus());assertArrayEquals(file.getBytes(),download.getContentAsByteArray());
        assertEquals(403,mvc.perform(multipart("/tickets/"+id+"/attachment").file(file).header("Authorization","Bearer "+tokens.generateToken(otherRequester))).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(multipart("/tickets/"+id+"/attachment").file(file).header("Authorization","Bearer "+tokens.generateToken(owner))).andReturn().getResponse().getStatus());
        assertTrue(ok("GET","/dashboard/summary",null,admin,200).isObject());
        assertTrue(ok("GET","/tickets/"+id+"/audit",null,tech,200).size()>=7);
        ok("GET","/tickets/"+id+"/audit",null,owner,403);
    }
    @Test void attachmentLimitsAndCommentChronology()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CRIADO,null);String jwt=tokens.generateToken(requester);
        for(var file:List.of(new MockMultipartFile("file","empty.png","image/png",new byte[0]),new MockMultipartFile("file","doc.txt","text/plain",new byte[]{1}),new MockMultipartFile("file","large.png","image/png",new byte[2*1024*1024+1]))){assertEquals(400,mvc.perform(multipart("/tickets/"+t.getId()+"/attachment").file(file).header("Authorization","Bearer "+jwt)).andReturn().getResponse().getStatus());}
        long first=ok("POST","/tickets/"+t.getId()+"/comments",Map.of("content","Primeiro"),requester,201).path("id").asLong();
        long second=ok("POST","/tickets/"+t.getId()+"/comments",Map.of("content","Segundo"),tech,201).path("id").asLong();
        jdbc.update("UPDATE ticket_comments SET created_at='2026-11-21 12:00:00' WHERE ticket_id=?",t.getId());
        var rows=ok("GET","/tickets/"+t.getId()+"/comments",null,requester,200);assertEquals(first,rows.get(0).path("id").asLong());assertEquals(second,rows.get(1).path("id").asLong());
    }
    @Test void concurrentAttachmentsNeverOverwriteTheWinner()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CRIADO,null);String jwt=tokens.generateToken(requester);
        java.util.function.Function<String,Callable<Result>> upload=name->()->{
            var file=new MockMultipartFile("file",name,"image/png",com.helpdeskpro.backend.TestImages.png());
            var response=mvc.perform(multipart("/tickets/"+t.getId()+"/attachment").file(file).header("Authorization","Bearer "+jwt)).andReturn().getResponse();
            return new Result(response.getStatus(),json.readTree(response.getContentAsString()));
        };
        var results=race(upload.apply("first.png"),upload.apply("second.png"));assertEquals(List.of(200,400),results.stream().map(Result::status).sorted().toList());
        String winner=results.stream().filter(r->r.status==200).findFirst().orElseThrow().body.path("attachmentName").asText();
        assertEquals(winner,tickets.findById(t.getId()).orElseThrow().getAttachmentName());
    }
    @Test void requesterCannotTamperWithSolutionOnClosedTicketEither()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CANCELADO,null);
        ok("PATCH","/tickets/"+t.getId()+"/status",change(TicketStatus.CANCELADO,"Injeção após cancelamento",version(t.getId())),requester,403);
    }
    @Test void titleHonorsExistingDatabaseLimit()throws Exception {
        ok("POST","/tickets",Map.of("title","x".repeat(256),"description","Descrição válida","category","DUVIDA"),requester,400);
        ok("POST","/tickets",Map.of("title","x".repeat(255),"description","Descrição válida","category","DUVIDA"),requester,201);
    }
    @Test void uploadReturnsTheCommittedRevision()throws Exception {
        Ticket t=ticket(requester,TicketStatus.CRIADO,null);
        var file=new MockMultipartFile("file","revision.png","image/png",com.helpdeskpro.backend.TestImages.png());
        var r=mvc.perform(multipart("/tickets/"+t.getId()+"/attachment").file(file).header("Authorization","Bearer "+tokens.generateToken(requester))).andReturn().getResponse();
        assertEquals(200,r.getStatus());assertEquals(version(t.getId()),json.readTree(r.getContentAsString()).path("version").asLong());
    }
    private void preparePreview()throws Exception {
        String db=System.getenv("HELPDESK_OPERATIONAL_PREVIEW_DB");if(db==null)return;if(!db.matches("op_preview_[a-f0-9]+"))throw new IllegalArgumentException("Preview must be isolated");
        String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"),user=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),pwd=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD");
        try(var c=DriverManager.getConnection(base,user,pwd);var s=c.createStatement()){s.execute("CREATE DATABASE "+db);}
        String url=base.substring(0,base.lastIndexOf('/')+1)+db;
        org.flywaydb.core.Flyway.configure().dataSource(url,user,pwd).load().migrate();
        String previewHash=encoder.encode(System.getenv("HELPDESK_OPERATIONAL_PREVIEW_PASSWORD"));
        try(var c=DriverManager.getConnection(url,user,pwd);var s=c.prepareStatement("INSERT INTO users(name,email,password,role) VALUES (?,?,?,?)")){
            for(String[] row:new String[][]{{"Administrador Homologação","admin@operacao.test","ADMIN"},{"Técnica Homologação","tecnica@operacao.test","TECHNICIAN"},{"Outro Técnico","outro.tecnico@operacao.test","TECHNICIAN"},{"Solicitante Homologação","solicitante@operacao.test","REQUESTER"},{"Outro Solicitante","outro.solicitante@operacao.test","REQUESTER"}}){s.setString(1,row[0]);s.setString(2,row[1]);s.setString(3,previewHash);s.setString(4,row[2]);s.executeUpdate();}
        }
    }
}
