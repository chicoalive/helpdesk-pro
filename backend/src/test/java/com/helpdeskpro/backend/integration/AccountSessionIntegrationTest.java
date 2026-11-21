package com.helpdeskpro.backend.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AccountSessionIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TokenService tokens;
    @Value("${api.security.token.secret}") private String secret;
    private MockMvc mvc;
    private User admin;
    private static final String PASSWORD = "test-only-strong-password";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        admin = create(UserRole.ADMIN);
    }

    private User create(UserRole role) {
        return users.saveAndFlush(new User("Session Test", UUID.randomUUID() + "@example.test",
                encoder.encode(PASSWORD), role));
    }

    private String loginBody(User user) {
        return "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    @Test
    void activeAdminCanLogin() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        assertTrue(admin.isEnabled());
        assertEquals(0, admin.getSessionVersion());
    }

    @Test
    void inactiveAccountCannotLogin() throws Exception {
        admin.setActive(false);
        users.saveAndFlush(admin);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(admin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void versionChangeRevokesTokenAndNewLoginStillWorks() throws Exception {
        String old = tokens.generateToken(admin);
        assertEquals(1, users.revokeSessionsById(admin.getId()));
        mvc.perform(get("/users").header("Authorization", "Bearer " + old))
                .andExpect(status().isUnauthorized());
        User refreshed = users.findById(admin.getId()).orElseThrow();
        mvc.perform(get("/users").header("Authorization", "Bearer " + tokens.generateToken(refreshed)))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveAccountIsRejectedEvenWhenTokenVersionMatches() throws Exception {
        admin.setActive(false);
        users.saveAndFlush(admin);
        mvc.perform(get("/users").header("Authorization", "Bearer " + tokens.generateToken(admin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reactivationDoesNotRestoreOldSessions() throws Exception {
        String old = tokens.generateToken(admin);
        admin.setActive(false);
        admin.setActive(true);
        users.saveAndFlush(admin);
        mvc.perform(get("/users").header("Authorization", "Bearer " + old))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identitySurvivesEmailChangeAndDoesNotTransferToAnotherAccount() throws Exception {
        String token = tokens.generateToken(admin);
        String previousEmail = admin.getEmail();
        admin.setEmail(UUID.randomUUID() + "@example.test");
        users.saveAndFlush(admin);
        users.saveAndFlush(new User("Other", previousEmail, encoder.encode(PASSWORD), UserRole.REQUESTER));
        mvc.perform(get("/users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals(admin.getId().toString(), JWT.decode(token).getSubject());
    }

    @Test
    void rolePermissionsComeFromCurrentDatabaseState() throws Exception {
        for (UserRole role : UserRole.values()) {
            User user = create(role);
            String token = tokens.generateToken(user);
            mvc.perform(get("/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().is(role == UserRole.ADMIN ? 200 : 403));
            mvc.perform(get("/assets/available").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        String old = tokens.generateToken(admin);
        admin.setRole(UserRole.REQUESTER);
        users.saveAndFlush(admin);
        mvc.perform(get("/users").header("Authorization", "Bearer " + old)).andExpect(status().isForbidden());
    }

    @Test
    void bothIndependentAdministratorsRemainUsable() throws Exception {
        User second = create(UserRole.ADMIN);
        for (User user : new User[]{admin, second}) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(user)))
                    .andExpect(status().isOk());
            mvc.perform(get("/users").header("Authorization", "Bearer " + tokens.generateToken(user)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void legacyAndIncompleteTokensAreRejected() throws Exception {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        Date expiration = Date.from(Instant.now().plusSeconds(300));
        String[] invalid = {
                JWT.create().withIssuer("helpdesk-pro").withSubject(admin.getEmail())
                        .withClaim("id", admin.getId()).withExpiresAt(expiration).sign(algorithm),
                JWT.create().withIssuer("helpdesk-pro").withSubject(admin.getId().toString())
                        .withClaim("id", admin.getId()).withExpiresAt(expiration).sign(algorithm),
                JWT.create().withIssuer("helpdesk-pro").withSubject(admin.getId().toString())
                        .withClaim("id", admin.getId()).withClaim("sessionVersion", 0L).sign(algorithm),
                JWT.create().withIssuer("helpdesk-pro").withSubject(admin.getId().toString())
                        .withClaim("id", admin.getId()).withClaim("sessionVersion", -1L)
                        .withExpiresAt(expiration).sign(algorithm),
                JWT.create().withIssuer("helpdesk-pro").withSubject("999999")
                        .withClaim("id", admin.getId()).withClaim("sessionVersion", 0L)
                        .withExpiresAt(expiration).sign(algorithm),
                JWT.create().withIssuer("helpdesk-pro").withSubject(admin.getId().toString())
                        .withClaim("id", admin.getId()).withClaim("sessionVersion", "invalid")
                        .withExpiresAt(expiration).sign(algorithm),
                tokens.generateToken(admin) + "tampered"
        };
        for (String token : invalid) {
            mvc.perform(get("/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void accountStateCannotBeInjectedThroughExistingCreateEndpoint() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/users").header("Authorization", "Bearer " + tokens.generateToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Employee\",\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD
                                + "\",\"role\":\"REQUESTER\",\"active\":false,\"sessionVersion\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionVersion").doesNotExist());
        User created = users.findByEmail(email).orElseThrow();
        assertTrue(created.isEnabled());
        assertEquals(0, created.getSessionVersion());
    }
}
