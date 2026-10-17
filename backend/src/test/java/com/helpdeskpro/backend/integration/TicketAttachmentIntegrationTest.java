package com.helpdeskpro.backend.integration;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketCommentRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("Testes de Integração - Anexo de Imagem no Chamado (Etapa 11)")
class TicketAttachmentIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private User admin;
    private User requester1;
    private User requester2;
    private User tech1;

    private String adminToken;
    private String req1Token;
    private String req2Token;
    private String tech1Token;

    private Ticket req1Ticket;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ticketCommentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("Admin", "admin@att.com", passwordEncoder.encode("123"), UserRole.ADMIN));
        requester1 = userRepository.save(new User("Requester 1", "req1@att.com", passwordEncoder.encode("123"), UserRole.REQUESTER));
        requester2 = userRepository.save(new User("Requester 2", "req2@att.com", passwordEncoder.encode("123"), UserRole.REQUESTER));
        tech1 = userRepository.save(new User("Tech 1", "tech1@att.com", passwordEncoder.encode("123"), UserRole.TECHNICIAN));

        adminToken = tokenService.generateToken(admin);
        req1Token = tokenService.generateToken(requester1);
        req2Token = tokenService.generateToken(requester2);
        tech1Token = tokenService.generateToken(tech1);

        req1Ticket = ticketRepository.save(new Ticket(
                "Erro na tela de login",
                "Aparece mensagem de erro ao clicar em entrar",
                TicketCategory.INCIDENTE_SISTEMA,
                TicketPriority.ALTA,
                requester1,
                null
        ));
    }

    @Test
    @DisplayName("Upload PNG válido por REQUESTER dono salva anexo, expõe metadados e não expõe bytes no JSON")
    void uploadValidPng_ByOwnerRequester_Success() throws Exception {
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "print-erro.png",
                "image/png",
                pngBytes
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(req1Ticket.getId().intValue())))
                .andExpect(jsonPath("$.hasAttachment", is(true)))
                .andExpect(jsonPath("$.attachmentName", is("print-erro.png")))
                .andExpect(jsonPath("$.attachmentContentType", is("image/png")))
                .andExpect(jsonPath("$.attachmentData").doesNotExist());

        mockMvc.perform(get("/tickets/" + req1Ticket.getId())
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment", is(true)))
                .andExpect(jsonPath("$.attachmentName", is("print-erro.png")))
                .andExpect(jsonPath("$.attachmentContentType", is("image/png")))
                .andExpect(jsonPath("$.attachmentData").doesNotExist());
    }

    @Test
    @DisplayName("Upload JPEG válido por REQUESTER dono retorna 200 OK")
    void uploadValidJpeg_ByOwnerRequester_Success() throws Exception {
        byte[] jpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "foto-tela.jpg",
                "image/jpeg",
                jpgBytes
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment", is(true)))
                .andExpect(jsonPath("$.attachmentName", is("foto-tela.jpg")))
                .andExpect(jsonPath("$.attachmentContentType", is("image/jpeg")))
                .andExpect(jsonPath("$.attachmentData").doesNotExist());
    }

    @Test
    @DisplayName("Upload de arquivo maior que 2 MB é rejeitado com 400 Bad Request")
    void uploadFileLargerThan2MB_Returns400() throws Exception {
        byte[] oversizedBytes = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "grande.png",
                "image/png",
                oversizedBytes
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Upload de tipo diferente de PNG/JPEG é rejeitado com 400 Bad Request")
    void uploadInvalidContentType_Returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "documento.pdf",
                "application/pdf",
                new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Upload de arquivo vazio é rejeitado com 400 Bad Request")
    void uploadEmptyFile_Returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "vazio.png",
                "image/png",
                new byte[0]
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Sobrescrever anexo já existente é rejeitado com 400 Bad Request")
    void uploadWhenAttachmentAlreadyExists_Returns400() throws Exception {
        MockMultipartFile firstFile = new MockMultipartFile(
                "file",
                "primeiro.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                "segundo.png",
                "image/png",
                new byte[]{4, 5, 6}
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(firstFile)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(secondFile)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("REQUESTER de outro chamado não consegue anexar (403 Forbidden)")
    void uploadByOtherRequester_Returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "print.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + req2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("ADMIN e TECHNICIAN não podem fazer upload de anexo (403 Forbidden)")
    void uploadByAdminOrTechnician_Returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "print.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + tech1Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("Download do anexo permitido para REQUESTER dono, ADMIN e TECHNICIAN, bloqueado para outro REQUESTER e sem token")
    void downloadAttachment_PermissionsAndBytes() throws Exception {
        byte[] imageBytes = new byte[]{10, 20, 30, 40, 50};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evidencia.png",
                "image/png",
                imageBytes
        );

        mockMvc.perform(multipart("/tickets/" + req1Ticket.getId() + "/attachment")
                        .file(file)
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk());

        // REQUESTER dono -> 200 OK
        mockMvc.perform(get("/tickets/" + req1Ticket.getId() + "/attachment")
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));

        // ADMIN -> 200 OK
        mockMvc.perform(get("/tickets/" + req1Ticket.getId() + "/attachment")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));

        // TECHNICIAN -> 200 OK
        mockMvc.perform(get("/tickets/" + req1Ticket.getId() + "/attachment")
                        .header("Authorization", "Bearer " + tech1Token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));

        // REQUESTER de outro chamado -> 403 Forbidden
        mockMvc.perform(get("/tickets/" + req1Ticket.getId() + "/attachment")
                        .header("Authorization", "Bearer " + req2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // Sem autenticação -> 401 Unauthorized
        mockMvc.perform(get("/tickets/" + req1Ticket.getId() + "/attachment"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Chamado sem anexo retorna hasAttachment=false no DTO e 404 Not Found em GET /tickets/{id}/attachment")
    void ticketWithoutAttachment_ReturnsFalseInDto_And404OnDownload() throws Exception {
        mockMvc.perform(get("/tickets/" + req1Ticket.getId())
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment", is(false)))
                .andExpect(jsonPath("$.attachmentName", is(nullValue())))
                .andExpect(jsonPath("$.attachmentContentType", is(nullValue())))
                .andExpect(jsonPath("$.attachmentData").doesNotExist());

        mockMvc.perform(get("/tickets/" + req1Ticket.getId() + "/attachment")
                        .header("Authorization", "Bearer " + req1Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}
