package com.helpdeskpro.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.dto.UserUpdateRequestDTO;
import com.helpdeskpro.backend.repositories.*;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class UserStateIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private UserAuditLogRepository audits;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder encoder;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User actor;
    private User target;
    private static final String PASSWORD = "test-only-password-strong";

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        actor = create(UserRole.ADMIN);
        target = create(UserRole.TECHNICIAN);
    }
    private User create(UserRole role) {
        return users.saveAndFlush(new User("Original Name", UUID.randomUUID()+"@test.local", encoder.encode(PASSWORD), role));
    }
    private ResultActions update(User user, String name, String email, UserRole role, long version) throws Exception {
        return mvc.perform(put("/users/"+user.getId()).header("Authorization","Bearer "+tokens.generateToken(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new UserUpdateRequestDTO(name,email,role,version))));
    }
    private void assertAccess(String token, int status) throws Exception {
        mvc.perform(get("/assets/available").header("Authorization","Bearer "+token)).andExpect(status().is(status));
    }


    private ResultActions state(User user, boolean active, long version) throws Exception {
        return mvc.perform(patch("/users/"+user.getId()+"/state")
            .header("Authorization", "Bearer "+tokens.generateToken(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("active", active, "expectedVersion", version))));
    }

    @ParameterizedTest
    @EnumSource(value=UserRole.class,names={"TECHNICIAN","REQUESTER"})
    void deactivateReactivatePreservesCredentialsAndRevokesEveryOldSession(UserRole role) throws Exception {
        target.setRole(role); users.saveAndFlush(target);
        long version=target.getEditVersion();
        String old=tokens.generateToken(target), hash=target.getPassword();
        state(target,false,version).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertAccess(old,401);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("email",target.getEmail(),"password",PASSWORD))))
            .andExpect(status().isUnauthorized());
        var logs=audits.findByUserIdOrderByOccurredAtAsc(target.getId());
        assertEquals(1,logs.size()); assertEquals("USER_DEACTIVATED",logs.getFirst().getOperation());
        assertEquals(actor.getId(),logs.getFirst().getActor().getId());
        assertEquals("true",logs.getFirst().getChanges().get("active").getOldValue());
        assertEquals("false",logs.getFirst().getChanges().get("active").getNewValue());
        assertNotNull(logs.getFirst().getOccurredAt());
        state(target,false,target.getEditVersion()).andExpect(status().isOk());
        assertEquals(1,audits.findByUserIdOrderByOccurredAtAsc(target.getId()).size());
        state(target,true,target.getEditVersion()).andExpect(status().isOk());
        assertAccess(old,401);
        assertEquals(hash,target.getPassword()); assertEquals(role,target.getRole());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("email",target.getEmail(),"password",PASSWORD))))
            .andExpect(status().isOk());
        assertAccess(tokens.generateToken(target),200);
        logs=audits.findByUserIdOrderByOccurredAtAsc(target.getId());
        assertEquals(2,logs.size()); assertEquals("USER_REACTIVATED",logs.getLast().getOperation());
        state(target,false,version).andExpect(status().isConflict());
        assertEquals(2,audits.findByUserIdOrderByOccurredAtAsc(target.getId()).size());
    }

    @ParameterizedTest
    @EnumSource(value=UserRole.class,names={"TECHNICIAN","REQUESTER"})
    void nonAdministratorsCannotChangeState(UserRole role)throws Exception {
        actor.setRole(role); users.saveAndFlush(actor);
        state(target,false,0).andExpect(status().isForbidden());
        assertTrue(target.isEnabled());
    }

    @Test
    void blocksSelfDeactivationAndRejectsInvalidRequests()throws Exception {
        state(actor,false,actor.getEditVersion()).andExpect(status().isBadRequest());
        assertTrue(actor.isEnabled());
        mvc.perform(patch("/users/"+target.getId()+"/state")
            .header("Authorization","Bearer "+tokens.generateToken(actor)).contentType(MediaType.APPLICATION_JSON)
            .content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(patch("/users/"+target.getId()+"/state")
            .header("Authorization","Bearer "+tokens.generateToken(actor)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"active\":false,\"expectedVersion\":0,\"role\":\"ADMIN\",\"password\":\"ignored\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TECHNICIAN"))
            .andExpect(jsonPath("$.password").doesNotExist());
    }
}
