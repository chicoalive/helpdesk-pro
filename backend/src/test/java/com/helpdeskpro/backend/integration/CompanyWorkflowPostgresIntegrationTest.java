package com.helpdeskpro.backend.integration;

import com.fasterxml.jackson.databind.*;
import com.helpdeskpro.backend.bootstrap.FirstAdminProvisioner;
import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="HELPDESK_COMPANY_DB",matches=".+")
class CompanyWorkflowPostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry)throws Exception {
        String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL"), name=System.getenv("HELPDESK_COMPANY_DB");
        if(!name.matches("company_[a-f0-9]+")) throw new IllegalArgumentException("Isolated database required");
        try(var c=DriverManager.getConnection(base,System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"),System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));var st=c.createStatement()){st.execute("CREATE DATABASE "+name);}
        registry.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+name);
        registry.add("spring.datasource.username",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER"));
        registry.add("spring.datasource.password",()->System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate"); registry.add("spring.flyway.enabled",()->true);
    }
    @Autowired WebApplicationContext context;
    @Autowired FirstAdminProvisioner provisioner;
    @Autowired UserRepository users;
    @Autowired UserAuditLogRepository audits;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper();
    private MockMvc mvc;
    private String password;
    private String jwt;
    private JsonNode call(String method,String path,Object body,int status)throws Exception {
        if (method.equals("PATCH") && path.endsWith("/technician") && body instanceof Map<?,?> original) {
            var revised = new HashMap<String,Object>(); original.forEach((k,v)->revised.put(k.toString(),v));
            revised.putIfAbsent("expectedVersion",jdbc.queryForObject("SELECT version FROM tickets WHERE id=?",Long.class,Long.valueOf(path.split("/")[2])));
            body = revised;
        }
        var request=switch(method){case "POST"->post(path);case "PUT"->put(path);case "PATCH"->patch(path);default->get(path);};
        if(jwt!=null)request.header("Authorization","Bearer "+jwt);
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response=mvc.perform(request).andReturn().getResponse();
        assertEquals(status,response.getStatus(),method+" "+path);
        String content=response.getContentAsString();
        if(!path.equals("/auth/login")){assertFalse(content.contains("\"password\""));assertFalse(content.contains("\"sessionVersion\""));assertFalse(content.contains(password));}
        return content.isEmpty()?json.nullNode():json.readTree(content);
    }
    private String login(String email)throws Exception {
        jwt=null; return call("POST","/auth/login",Map.of("email",email,"password",password),200).get("token").asText();
    }
    private User create(String name,String email,UserRole role)throws Exception {
        long id=call("POST","/users",Map.of("name",name,"email",email,"password",password,"role",role),200).get("id").asLong();
        return users.findById(id).orElseThrow();
    }
    private void state(User user,boolean active,int status)throws Exception {
        call("PATCH","/users/"+user.getId()+"/state",Map.of("active",active,"expectedVersion",users.findById(user.getId()).orElseThrow().getEditVersion()),status);
    }
    private String fingerprint(String table){return jdbc.queryForObject("SELECT md5(COALESCE(string_agg(row_to_json(t)::text,',' ORDER BY id),'')) FROM "+table+" t",String.class);}

    @Test
    void companyUsesAccountsSessionsTicketsAndAuditTogether()throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        password=System.getenv("HELPDESK_COMPANY_PASSWORD"); assertNotNull(password);
        assertEquals(0,users.count());
        assertEquals(10L,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Long.class));
        assertTrue(provisioner.create("Helena Administradora","admin1@empresa.test",password.toCharArray()));
        assertFalse(provisioner.create("Blocked","blocked@empresa.test",password.toCharArray()));
        String admin=login("admin1@empresa.test");jwt=admin;
        User admin2=create("Marcos Administrador","admin2@empresa.test",UserRole.ADMIN);
        User tech1=create("João Técnico","tecnico1@empresa.test",UserRole.TECHNICIAN);
        User tech2=create("Márcia Técnica","tecnico2@empresa.test",UserRole.TECHNICIAN);
        User req1=create("José Solicitante","solicitante1@empresa.test",UserRole.REQUESTER);
        User req2=create("Ana Solicitante","solicitante2@empresa.test",UserRole.REQUESTER);
        User req3=create("Paula Solicitante","solicitante3@empresa.test",UserRole.REQUESTER);
        assertEquals(7,users.count());
        for(User u:List.of(admin2,tech1,tech2,req1,req2,req3)){assertNotNull(login(u.getEmail()));}
        jwt=admin;
        long asset=call("POST","/assets",Map.of("code","EMP-NB-01","type","Notebook","description","Equipamento fictício","status","IN_USE"),201).get("id").asLong();
        call("POST","/assets",Map.of("code","EMP-NB-02","type","Notebook","status","IN_USE"),201);
        String requester=login(req1.getEmail());jwt=requester;
        long ticket=call("POST","/tickets",Map.of("title","Notebook sem conexão","description","Wi-fi indisponível","category","INCIDENTE_EQUIPAMENTO","assetId",asset),201).get("id").asLong();
        byte[] bytes=com.helpdeskpro.backend.TestImages.png();
        var attachment=new MockMultipartFile("file","evidencia.png","image/png",bytes);
        assertEquals(200,mvc.perform(multipart("/tickets/"+ticket+"/attachment").file(attachment).header("Authorization","Bearer "+jwt)).andReturn().getResponse().getStatus());
        call("POST","/tickets/"+ticket+"/comments",Map.of("content","Ocorrência registrada pelo funcionário."),201);
        jwt=admin;call("PATCH","/tickets/"+ticket+"/technician",Map.of("technicianId",tech1.getId(),"justification","Responsável anterior perdeu o perfil"),200);
        String technician=login(tech1.getEmail());jwt=technician;
        call("GET","/tickets/"+ticket,null,200);
        call("PATCH","/tickets/"+ticket+"/status",Map.of("status","EM_ATENDIMENTO"),200);
        call("POST","/tickets/"+ticket+"/comments",Map.of("content","Diagnóstico de rede iniciado."),201);
        for(String token:List.of(technician,requester)){
            jwt=token;call("GET","/users",null,403);
            call("POST","/users",Map.of("name","Escalada","email","escalada@empresa.test","password",password,"role","ADMIN"),403);
            call("PATCH","/users/"+req3.getId()+"/state",Map.of("active",false,"expectedVersion",0),403);
        }
        jwt=requester;call("GET","/assets",null,403);call("GET","/tickets/requester/"+req2.getId(),null,403);
        String beforePassword=req2.getPassword();String old=login(req2.getEmail());jwt=admin;
        call("PUT","/users/"+req2.getId(),Map.of("name","Ana Nome Atualizado","email",req2.getEmail(),"role","REQUESTER","expectedVersion",0),200);
        jwt=old;call("GET","/assets/available",null,200);jwt=admin;
        call("PUT","/users/"+req2.getId(),Map.of("name","Ana Nome Atualizado","email","ana.nova@empresa.test","role","TECHNICIAN","expectedVersion",1),200);
        jwt=old;call("GET","/assets/available",null,401);jwt=login("ana.nova@empresa.test");call("GET","/dashboard/summary",null,200);
        assertTrue(beforePassword.equals(users.findById(req2.getId()).orElseThrow().getPassword()));
        jwt=admin;call("PUT","/users/"+req2.getId(),Map.of("name",req2.getName(),"email",req2.getEmail(),"role","REQUESTER","expectedVersion",0),409);
        long events=audits.count();call("POST","/users",Map.of("name","Duplicado","email","ADMIN1@EMPRESA.TEST","password",password,"role","ADMIN"),400);assertEquals(events,audits.count());
        var tables=List.of("tickets","assets","ticket_comments","ticket_audit_logs");var before=tables.stream().map(this::fingerprint).toList();
        state(tech1,false,200);jwt=technician;call("GET","/tickets",null,401);jwt=null;call("POST","/auth/login",Map.of("email",tech1.getEmail(),"password",password),401);
        jwt=admin;state(req1,false,200);assertEquals(before,tables.stream().map(this::fingerprint).toList());
        call("GET","/tickets/"+ticket,null,200);call("PATCH","/tickets/"+ticket+"/technician",Map.of("technicianId",tech1.getId()),400);
        call("PATCH","/tickets/"+ticket+"/technician",Map.of("technicianId",tech2.getId(),"justification","Continuidade do atendimento"),200);
        state(tech1,true,200);jwt=technician;call("GET","/tickets",null,401);jwt=login(tech1.getEmail());call("GET","/tickets",null,200);

        jwt=admin;
        assertTrue(Arrays.equals(bytes,jdbc.queryForObject("SELECT attachment_data FROM tickets WHERE id=?",byte[].class,ticket)));
        assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM ticket_comments WHERE ticket_id=?",Long.class,ticket));
        assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM assets",Long.class));
        assertEquals(Set.of("USER_CREATED","USER_UPDATED","USER_DEACTIVATED","USER_REACTIVATED"),new HashSet<>(jdbc.queryForList("SELECT DISTINCT operation FROM user_audit_logs",String.class)));
        assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM user_audit_logs WHERE actor_id IS NULL OR user_id IS NULL OR occurred_at IS NULL",Long.class));
        // Real PostgreSQL failure after a user flush must roll back account and audit together.
        jdbc.execute("CREATE FUNCTION reject_state_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated audit outage'; END $$");
        jdbc.execute("CREATE TRIGGER reject_state_audit BEFORE INSERT ON user_audit_logs FOR EACH ROW EXECUTE FUNCTION reject_state_audit()");
        String usersBefore=fingerprint("users"), auditsBefore=fingerprint("user_audit_logs");
        state(req3,false,500);
        assertEquals(usersBefore,fingerprint("users"));assertEquals(auditsBefore,fingerprint("user_audit_logs"));
        jdbc.execute("DROP TRIGGER reject_state_audit ON user_audit_logs");jdbc.execute("DROP FUNCTION reject_state_audit()");
        state(users.findByEmail("admin1@empresa.test").orElseThrow(),false,400);
        // Existing assignments must remain manageable after a technician changes role.
        call("PUT","/users/"+tech2.getId(),Map.of("name",tech2.getName(),"email",tech2.getEmail(),"role","REQUESTER","expectedVersion",0),200);
        call("PATCH","/tickets/"+ticket+"/technician",Map.of("technicianId",tech1.getId(),"justification","Continuidade do atendimento"),200);
        jwt=login(tech1.getEmail());call("PATCH","/tickets/"+ticket+"/status",Map.of("status","RESOLVIDO","solution","Configuração de rede corrigida."),200);
        call("PATCH","/tickets/"+ticket+"/status",Map.of("status","FECHADO"),200);
        jwt=login(req3.getEmail());
        long raceTicket=call("POST","/tickets",Map.of("title","Atribuição concorrente","description","Teste de dois atendentes","category","DUVIDA"),201).get("id").asLong();
        jwt=admin;
        jdbc.execute("CREATE FUNCTION slow_assignment() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_sleep(0.3); RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER slow_assignment BEFORE UPDATE ON tickets FOR EACH ROW WHEN (OLD.technician_id IS NULL) EXECUTE FUNCTION slow_assignment()");
        var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->assignConcurrently(admin,raceTicket,tech1.getId(),barrier));
            var second=pool.submit(()->assignConcurrently(admin,raceTicket,req2.getId(),barrier));
            var statuses=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertEquals(1,statuses.stream().filter(code->code==200).count(),"Only one concurrent assignment may succeed");
            assertEquals(1,statuses.stream().filter(code->code==400||code==409).count());
        } finally {jdbc.execute("DROP TRIGGER slow_assignment ON tickets");jdbc.execute("DROP FUNCTION slow_assignment()");}
    }
    private int assignConcurrently(String admin,long ticket,long technician,CyclicBarrier barrier)throws Exception {
        barrier.await(10,TimeUnit.SECONDS);
        return mvc.perform(patch("/tickets/"+ticket+"/technician").header("Authorization","Bearer "+admin)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("technicianId",technician,"expectedVersion",0L))))
            .andReturn().getResponse().getStatus();
    }
}
