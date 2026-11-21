package com.helpdeskpro.backend.integration;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.dto.UserCreateRequestDTO;
import com.helpdeskpro.backend.repositories.UserAuditLogRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.TokenService;
import com.helpdeskpro.backend.services.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class UserCreationAuditIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private UserAuditLogRepository audits;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UserService service;
    private MockMvc mvc;
    private User actor;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "test-only-strong-secret";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        actor = users.saveAndFlush(new User("Administrator", UUID.randomUUID() + "@test.local",
                encoder.encode(PASSWORD), UserRole.ADMIN));
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private UserCreateRequestDTO request(UserRole role) {
        return new UserCreateRequestDTO(" New Employee ", UUID.randomUUID() + "@test.local", PASSWORD, role);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void createsEveryRoleWithTransactionalAuditAndWorkingLogin(UserRole role, CapturedOutput output) throws Exception {
        var request = request(role);
        String result = mvc.perform(post("/users")
                        .header("Authorization", "Bearer " + tokens.generateToken(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.sessionVersion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        User created = users.findById(json.readTree(result).get("id").asLong()).orElseThrow();
        assertEquals("New Employee", created.getName());
        assertTrue(created.isEnabled());
        assertEquals(0, created.getSessionVersion());
        assertTrue(encoder.matches(PASSWORD, created.getPassword()));
        var logs = audits.findByUserIdOrderByOccurredAtAsc(created.getId());
        assertEquals(1, logs.size());
        var audit = logs.getFirst();
        assertEquals(actor.getId(), audit.getActor().getId());
        assertEquals(created.getId(), audit.getUser().getId());
        assertEquals(role, audit.getAssignedRole());
        assertEquals("USER_CREATED", audit.getOperation());
        assertNotNull(audit.getOccurredAt());

        String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", created.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("token").asText();
        assertEquals(created.getId().toString(), JWT.decode(token).getSubject());
        assertEquals(0L, JWT.decode(token).getClaim("sessionVersion").asLong());
        mvc.perform(get("/users").header("Authorization", "Bearer " + token))
                .andExpect(status().is(role == UserRole.ADMIN ? 200 : 403));
        mvc.perform(get("/assets/available").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertFalse(result.contains(PASSWORD));
        assertFalse(output.getAll().contains(PASSWORD));
        assertFalse(output.getAll().contains(created.getPassword()));
        assertFalse(output.getAll().contains(token));
        assertFalse(request.toString().contains(PASSWORD));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TECHNICIAN", "REQUESTER"})
    void cannotBypassPermissionsThroughApiOrService(UserRole role) throws Exception {
        actor.setRole(role);
        users.saveAndFlush(actor);
        long before = users.count();
        long auditBefore = audits.count();
        var request = request(UserRole.ADMIN);
        mvc.perform(post("/users").header("Authorization", "Bearer " + tokens.generateToken(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.createUser(request));
        assertEquals(before, users.count());
        assertEquals(auditBefore, audits.count());
    }

    @Test
    void duplicateNormalizedEmailDoesNotCreateAudit() throws Exception {
        var request = new UserCreateRequestDTO("Duplicate", "  " + actor.getEmail().toUpperCase() + "  ",
                PASSWORD, UserRole.ADMIN);
        long before = audits.count();
        mvc.perform(post("/users").header("Authorization", "Bearer " + tokens.generateToken(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertEquals(before, audits.count());
    }

    @Test
    void invalidInputsDoNotCreateAccountsOrAudits() throws Exception {
        String[] invalid = {
                "{}", "{\"name\":\"A\",\"email\":\"invalid\",\"password\":\"short\",\"role\":\"ADMIN\"}",
                "{\"name\":\"Employee\",\"email\":\"test@example.com\",\"password\":\"short\",\"role\":\"ROOT\"}",
                json.writeValueAsString(new UserCreateRequestDTO("Employee", "test@example.com", "á".repeat(37), UserRole.ADMIN))
        };
        long userBefore = users.count(), auditBefore = audits.count();
        for (String body : invalid) {
            mvc.perform(post("/users").header("Authorization", "Bearer " + tokens.generateToken(actor))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(userBefore, users.count());
        assertEquals(auditBefore, audits.count());
    }

    @Test
    void passwordWhitespaceIsPreserved() throws Exception {
        String password = "  test-password-exact  ";
        var request = new UserCreateRequestDTO("Employee", UUID.randomUUID() + "@test.local", password, UserRole.REQUESTER);
        mvc.perform(post("/users").header("Authorization", "Bearer " + tokens.generateToken(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
        assertTrue(encoder.matches(password, users.findByEmail(request.email()).orElseThrow().getPassword()));
    }
}
