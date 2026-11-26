package com.helpdeskpro.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.dto.UserUpdateRequestDTO;
import com.helpdeskpro.backend.repositories.*;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="HELPDESK_DISPOSABLE_TEST_PG_URL",matches=".+")
class UserEditingPostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String base=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_URL");
        String user=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_USER");
        String password=System.getenv("HELPDESK_DISPOSABLE_TEST_PG_PASSWORD");
        String name="editing_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(base,user,password);var statement=connection.createStatement()){
            statement.execute("CREATE DATABASE "+name);
        }
        registry.add("spring.datasource.url",()->base.substring(0,base.lastIndexOf('/')+1)+name);
        registry.add("spring.datasource.username",()->user);
        registry.add("spring.datasource.password",()->password);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        registry.add("spring.flyway.enabled",()->true);
    }
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private UserAuditLogRepository audits;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper();

    private User create(UserRole role){
        return users.saveAndFlush(new User("Concurrent Test",UUID.randomUUID()+"@test.local",
                encoder.encode("test-only-password-strong"),role));
    }

    private int edit(MockMvc mvc,User actor,User target,String name,UserRole role,CyclicBarrier barrier)throws Exception{
        barrier.await(10,TimeUnit.SECONDS);
        return mvc.perform(put("/users/"+target.getId()).header("Authorization","Bearer "+tokens.generateToken(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new UserUpdateRequestDTO(name,target.getEmail(),role,0L))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void concurrentDemotionsAndStaleEditsPreserveAdminAndHistoricalLinks()throws Exception{
        MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        User first=create(UserRole.ADMIN),second=create(UserRole.ADMIN);
        var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)){
            var a=workers.submit(()->edit(mvc,first,first,first.getName(),UserRole.REQUESTER,barrier));
            var b=workers.submit(()->edit(mvc,second,second,second.getName(),UserRole.REQUESTER,barrier));
            var statuses=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(1,statuses.stream().filter(s->s==200).count());
            assertEquals(1,statuses.stream().filter(s->s==400 || s==403 || s==409).count());
        }
        assertEquals(1,users.countByRoleAndActiveTrue(UserRole.ADMIN));
        assertEquals(1L,jdbc.queryForObject("SELECT active_admins FROM user_admin_guard WHERE id=1",Long.class));
        User survivor=users.findAll().stream().filter(u->u.isEnabled()&&u.getRole()==UserRole.ADMIN).findFirst().orElseThrow();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                ()->jdbc.update("UPDATE users SET role='REQUESTER' WHERE id=?",survivor.getId()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                ()->jdbc.update("UPDATE users SET active=false WHERE id=?",survivor.getId()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                ()->jdbc.update("DELETE FROM users WHERE id=?",survivor.getId()));
        User target=create(UserRole.REQUESTER),technician=create(UserRole.TECHNICIAN);
        jdbc.update("INSERT INTO assets(id,code,status,type) VALUES(1,'EDIT-LINK','IN_USE','Laptop')");
        jdbc.update("""
                INSERT INTO tickets(id,title,description,category,priority,status,created_at,requester_id,technician_id,
                asset_id,attachment_name,attachment_content_type,attachment_data)
                VALUES(1,'History','Preserve','DUVIDA','MEDIA','EM_ATENDIMENTO',now(),?,?,1,'a.bin',
                'application/octet-stream',decode('aabb00ff','hex'))
                """,target.getId(),technician.getId());
        jdbc.update("INSERT INTO ticket_comments(id,content,created_at,author_id,ticket_id) VALUES(1,'History',now(),?,1)",target.getId());
        jdbc.update("INSERT INTO ticket_audit_logs(id,changed_at,field_name,changed_by_id,ticket_id) VALUES(1,now(),'status',?,1)",survivor.getId());
        var tables=List.of("tickets","assets","ticket_comments","ticket_audit_logs");
        var before=tables.stream().map(this::fingerprint).toList();
        String originalPassword=target.getPassword();
        var sameTarget=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)){
            var a=workers.submit(()->edit(mvc,survivor,target,"First Edit",UserRole.REQUESTER,sameTarget));
            var b=workers.submit(()->edit(mvc,survivor,target,"Second Edit",UserRole.REQUESTER,sameTarget));
            var statuses=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(409));
        }
        assertEquals(1,audits.findByUserIdOrderByOccurredAtAsc(target.getId()).size());
        assertEquals(originalPassword,users.findById(target.getId()).orElseThrow().getPassword());
        assertEquals(before,tables.stream().map(this::fingerprint).toList());

        String technicianJwt=tokens.generateToken(technician);
        String requesterJwt=tokens.generateToken(target);
        String adminJwt=tokens.generateToken(survivor);
        assertEquals(200,state(mvc,adminJwt,technician,false,technician.getEditVersion()));
        assertEquals(200,state(mvc,adminJwt,target,false,users.findById(target.getId()).orElseThrow().getEditVersion()));
        assertEquals(before,tables.stream().map(this::fingerprint).toList());
        assertEquals(401,mvc.perform(get("/assets/available").header("Authorization","Bearer "+technicianJwt)).andReturn().getResponse().getStatus());
        assertEquals(401,mvc.perform(get("/assets/available").header("Authorization","Bearer "+requesterJwt)).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(get("/tickets/1").header("Authorization","Bearer "+adminJwt)).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(patch("/tickets/1/technician").header("Authorization","Bearer "+adminJwt)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("technicianId",technician.getId(),"expectedVersion",0)))).andReturn().getResponse().getStatus());
        jdbc.execute("SELECT setval(pg_get_serial_sequence('ticket_audit_logs','id'), (SELECT max(id) FROM ticket_audit_logs))");
        User replacement=create(UserRole.TECHNICIAN);
        assertEquals(200,mvc.perform(patch("/tickets/1/technician").header("Authorization","Bearer "+adminJwt)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("technicianId",replacement.getId(),"expectedVersion",0,"justification","Substituição de técnico inativo")))).andReturn().getResponse().getStatus());
        assertEquals(replacement.getId(),jdbc.queryForObject("SELECT technician_id FROM tickets WHERE id=1",Long.class));
        assertEquals(before.get(1),fingerprint("assets"));
        assertEquals(before.get(2),fingerprint("ticket_comments"));
        assertEquals(200,state(mvc,adminJwt,technician,true,users.findById(technician.getId()).orElseThrow().getEditVersion()));
        assertEquals(401,mvc.perform(get("/assets/available").header("Authorization","Bearer "+technicianJwt)).andReturn().getResponse().getStatus());
        var events=audits.findByUserIdOrderByOccurredAtAsc(technician.getId());
        assertEquals(List.of("USER_DEACTIVATED","USER_REACTIVATED"),events.stream().map(e->e.getOperation()).toList());
        User concurrent=create(UserRole.REQUESTER);
        CyclicBarrier stateBarrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> request=()->{stateBarrier.await(10,TimeUnit.SECONDS);return state(mvc,adminJwt,concurrent,false,0);};
            var a=pool.submit(request);var b=pool.submit(request);
            var statuses=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertTrue(statuses.contains(200)); assertTrue(statuses.contains(409));
        }
        assertEquals(1,audits.findByUserIdOrderByOccurredAtAsc(concurrent.getId()).size());
        assertEquals(400,state(mvc,adminJwt,survivor,false,survivor.getEditVersion()));

        User otherAdmin=create(UserRole.ADMIN);
        String otherJwt=tokens.generateToken(otherAdmin);
        var adminBarrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{adminBarrier.await(10,TimeUnit.SECONDS);return state(mvc,adminJwt,otherAdmin,false,0);});
            var b=pool.submit(()->{adminBarrier.await(10,TimeUnit.SECONDS);return state(mvc,otherJwt,survivor,false,survivor.getEditVersion());});
            var statuses=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(1,statuses.stream().filter(code->code==200).count());
            assertEquals(1,statuses.stream().filter(code->code==401||code==403||code==409).count());
        }
        assertEquals(1,users.countByRoleAndActiveTrue(UserRole.ADMIN));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE users SET active=false WHERE role='ADMIN' AND active"));
        assertEquals("aabb00ff",jdbc.queryForObject("SELECT encode(attachment_data,'hex') FROM tickets WHERE id=1",String.class));
    }


    private int state(MockMvc mvc,String jwt,User user,boolean active,long version)throws Exception {
        return mvc.perform(patch("/users/"+user.getId()+"/state").header("Authorization","Bearer "+jwt)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("active",active,"expectedVersion",version))))
            .andReturn().getResponse().getStatus();
    }
    private String fingerprint(String table){
        return jdbc.queryForObject("SELECT md5(string_agg(row_to_json(t)::text,',' ORDER BY id)) FROM "+table+" t",String.class);
    }
}
