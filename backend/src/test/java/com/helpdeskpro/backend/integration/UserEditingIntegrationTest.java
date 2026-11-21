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
class UserEditingIntegrationTest {
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

    @Test
    void nameOnlyChangePreservesIdentityPasswordAndSession() throws Exception {
        String token = tokens.generateToken(target), hash = target.getPassword();
        update(target," New Name ",target.getEmail(),target.getRole(),0)
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.sessionVersion").doesNotExist());
        User changed=users.findById(target.getId()).orElseThrow();
        assertEquals(hash,changed.getPassword());
        assertEquals(0,changed.getSessionVersion());
        assertAccess(token,200);
        var log=audits.findByUserIdOrderByOccurredAtAsc(target.getId()).getFirst();
        assertEquals("USER_UPDATED",log.getOperation());
        assertEquals(actor.getId(),log.getActor().getId());
        assertEquals(1,log.getChanges().size());
        assertEquals("Original Name",log.getChanges().get("name").getOldValue());
        assertEquals("New Name",log.getChanges().get("name").getNewValue());
        assertNotNull(log.getOccurredAt());
    }

    @Test
    void emailChangeRevokesOldJwtAndSupportsNewLogin() throws Exception {
        String token=tokens.generateToken(target);
        String email=UUID.randomUUID()+"@example.test";
        update(target,target.getName()," "+email.toUpperCase()+" ",target.getRole(),0).andExpect(status().isOk());
        assertAccess(token,401);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email",email,"password",PASSWORD))))
                .andExpect(status().isOk());
        assertEquals(email,audits.findByUserIdOrderByOccurredAtAsc(target.getId()).getFirst()
                .getChanges().get("email").getNewValue());
    }

    @Test
    void profileChangeRevokesSessions() throws Exception {
        String token=tokens.generateToken(target);
        update(target,target.getName(),target.getEmail(),UserRole.REQUESTER,0).andExpect(status().isOk());
        assertAccess(token,401);
        assertEquals(UserRole.REQUESTER,users.findById(target.getId()).orElseThrow().getRole());
    }

    @ParameterizedTest
    @EnumSource(value=UserRole.class,names={"TECHNICIAN","REQUESTER"})
    void nonAdminCannotEdit(UserRole role) throws Exception {
        actor.setRole(role);users.saveAndFlush(actor);
        update(target,"Forbidden",target.getEmail(),target.getRole(),0).andExpect(status().isForbidden());
        assertTrue(audits.findByUserIdOrderByOccurredAtAsc(target.getId()).isEmpty());
    }

    @Test
    void duplicateNormalizedEmailIsRejected() throws Exception {
        update(target,target.getName()," "+actor.getEmail().toUpperCase()+" ",target.getRole(),0)
                .andExpect(status().isBadRequest());
        assertTrue(audits.findByUserIdOrderByOccurredAtAsc(target.getId()).isEmpty());
    }

    @Test
    void invalidAndInternalFieldsAreHandledSafely() throws Exception {
        mvc.perform(put("/users/"+target.getId()).header("Authorization","Bearer "+tokens.generateToken(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\",\"email\":\"invalid\",\"role\":\"ROOT\",\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest());
        String hash=target.getPassword();
        String body=json.writeValueAsString(new UserUpdateRequestDTO("Safe",target.getEmail(),target.getRole(),0L));
        body=body.substring(0,body.length()-1)+",\"id\":999,\"password\":\"forbidden\",\"active\":false,\"sessionVersion\":999}";
        mvc.perform(put("/users/"+target.getId()).header("Authorization","Bearer "+tokens.generateToken(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId())).andExpect(jsonPath("$.active").value(true));
        User changed=users.findById(target.getId()).orElseThrow();
        assertEquals(hash,changed.getPassword());
        assertEquals(0,changed.getSessionVersion());
    }

    @Test
    void lastActiveAdminCannotBeDemoted() throws Exception {
        update(actor,actor.getName(),actor.getEmail(),UserRole.REQUESTER,actor.getEditVersion())
                .andExpect(status().isBadRequest());
        assertEquals(UserRole.ADMIN,users.findById(actor.getId()).orElseThrow().getRole());
    }

    @Test
    void selfDemotionRevokesOwnTokenWhenAnotherAdminExists() throws Exception {
        create(UserRole.ADMIN);
        String old=tokens.generateToken(actor);
        update(actor,actor.getName(),actor.getEmail(),UserRole.REQUESTER,0).andExpect(status().isOk());
        assertAccess(old,401);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email",actor.getEmail(),"password",PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void staleEditReturnsConflictWithoutOverwriting() throws Exception {
        update(target,"First",target.getEmail(),target.getRole(),0).andExpect(status().isOk());
        update(target,"Stale",target.getEmail(),target.getRole(),0).andExpect(status().isConflict());
        assertEquals("First",users.findById(target.getId()).orElseThrow().getName());
        assertEquals(1,audits.findByUserIdOrderByOccurredAtAsc(target.getId()).size());
    }

    @Test
    void unchangedValuesProduceNoAuditOrVersionBump() throws Exception {
        update(target,target.getName(),target.getEmail(),target.getRole(),0).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        assertTrue(audits.findByUserIdOrderByOccurredAtAsc(target.getId()).isEmpty());
    }
}
