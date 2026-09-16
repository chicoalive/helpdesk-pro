package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.Asset;
import com.helpdeskpro.backend.domain.AssetStatus;
import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketPriority;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.TicketAssignTechnicianDTO;
import com.helpdeskpro.backend.dto.TicketRequestDTO;
import com.helpdeskpro.backend.dto.TicketResponseDTO;
import com.helpdeskpro.backend.dto.TicketStatusUpdateDTO;
import com.helpdeskpro.backend.exceptions.AssetNotFoundException;
import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import com.helpdeskpro.backend.exceptions.TicketNotFoundException;
import com.helpdeskpro.backend.exceptions.UserNotFoundException;
import com.helpdeskpro.backend.repositories.AssetRepository;
import com.helpdeskpro.backend.repositories.TicketRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private TicketService ticketService;

    private User requester;
    private User technician;
    private Asset activeAsset;

    @BeforeEach
    void setUp() {
        requester = new User("Usuario Solicitante", "user@helpdesk.com", "123", UserRole.REQUESTER);
        technician = new User("Tecnico Suporte", "tech@helpdesk.com", "123", UserRole.TECHNICIAN);
        ReflectionTestUtils.setField(requester, "id", 1L);
        ReflectionTestUtils.setField(technician, "id", 2L);
        activeAsset = new Asset(1L, "NOTE-001", "Notebook", "Dell Latitude", AssetStatus.IN_USE);
    }

    @Test
    @DisplayName("Deve criar chamado com prioridade ALTA para INCIDENTE_SISTEMA e status CRIADO com técnico nulo")
    void createTicket_IncidenteSistema_PriorityAlta() {
        TicketRequestDTO dto = new TicketRequestDTO("Erro no ERP", "Sistema fora do ar", TicketCategory.INCIDENTE_SISTEMA, 1L, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(10L);
            return t;
        });

        TicketResponseDTO response = ticketService.createTicket(dto);

        assertNotNull(response);
        assertEquals(TicketStatus.CRIADO, response.getStatus());
        assertNull(response.getTechnicianId());
        assertEquals(TicketPriority.ALTA, response.getPriority());
        assertEquals("Erro no ERP", response.getTitle());
    }

    @Test
    @DisplayName("Deve criar chamado com prioridade BAIXA para DUVIDA")
    void createTicket_Duvida_PriorityBaixa() {
        TicketRequestDTO dto = new TicketRequestDTO("Duvida sobre VPN", "Como conectar?", TicketCategory.DUVIDA, 1L, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.createTicket(dto);

        assertEquals(TicketPriority.BAIXA, response.getPriority());
    }

    @Test
    @DisplayName("Deve criar chamado com prioridade MEDIA para SOLICITACAO_ACESSO e OUTRO")
    void createTicket_Outro_PriorityMedia() {
        TicketRequestDTO dto = new TicketRequestDTO("Outro assunto", "Descricao", TicketCategory.OUTRO, 1L, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.createTicket(dto);

        assertEquals(TicketPriority.MEDIA, response.getPriority());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException ao criar INCIDENTE_EQUIPAMENTO sem assetId")
    void createTicket_IncidenteEquipamento_WithoutAsset_ThrowsException() {
        TicketRequestDTO dto = new TicketRequestDTO("Tela azul", "Notebook travou", TicketCategory.INCIDENTE_EQUIPAMENTO, 1L, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.createTicket(dto));
        assertEquals("Para a categoria INCIDENTE_EQUIPAMENTO, o envio do ativo (assetId) é obrigatório.", ex.getMessage());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar AssetNotFoundException se assetId informado não existir")
    void createTicket_AssetNotFound_ThrowsException() {
        TicketRequestDTO dto = new TicketRequestDTO("Tela azul", "Notebook travou", TicketCategory.INCIDENTE_EQUIPAMENTO, 1L, 99L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AssetNotFoundException.class, () -> ticketService.createTicket(dto));
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException se o ativo vinculado estiver INACTIVE")
    void createTicket_InactiveAsset_ThrowsException() {
        Asset inactiveAsset = new Asset(2L, "MON-001", "Monitor", "LG", AssetStatus.INACTIVE);
        TicketRequestDTO dto = new TicketRequestDTO("Problema monitor", "Pisca sem parar", TicketCategory.INCIDENTE_EQUIPAMENTO, 1L, 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(inactiveAsset));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.createTicket(dto));
        assertEquals("Somente ativos com status IN_USE podem ser vinculados a chamados.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException se o ativo vinculado estiver DISCARDED")
    void createTicket_DiscardedAsset_ThrowsException() {
        Asset discardedAsset = new Asset(3L, "PC-001", "Desktop", "Antigo", AssetStatus.DISCARDED);
        TicketRequestDTO dto = new TicketRequestDTO("Problema PC", "Nao liga", TicketCategory.INCIDENTE_EQUIPAMENTO, 1L, 3L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(assetRepository.findById(3L)).thenReturn(Optional.of(discardedAsset));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.createTicket(dto));
        assertEquals("Somente ativos com status IN_USE podem ser vinculados a chamados.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve criar INCIDENTE_EQUIPAMENTO com sucesso quando ativo válido estiver associado")
    void createTicket_IncidenteEquipamento_Success() {
        TicketRequestDTO dto = new TicketRequestDTO("Teclado quebrado", "Tecla falhando", TicketCategory.INCIDENTE_EQUIPAMENTO, 1L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(assetRepository.findById(1L)).thenReturn(Optional.of(activeAsset));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.createTicket(dto);

        assertNotNull(response);
        assertEquals(TicketPriority.MEDIA, response.getPriority());
        assertEquals("NOTE-001", response.getAssetCode());
    }

    @Test
    @DisplayName("Deve lançar UserNotFoundException ao criar chamado com solicitante inexistente")
    void createTicket_RequesterNotFound_ThrowsException() {
        TicketRequestDTO dto = new TicketRequestDTO("Titulo", "Desc", TicketCategory.DUVIDA, 99L, null);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> ticketService.createTicket(dto));
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException se o solicitante não possuir o papel REQUESTER")
    void createTicket_RequesterInvalidRole_ThrowsException() {
        User adminUser = new User("Admin", "admin@helpdesk.com", "123", UserRole.ADMIN);
        ReflectionTestUtils.setField(adminUser, "id", 10L);
        TicketRequestDTO dto = new TicketRequestDTO("Titulo", "Desc", TicketCategory.DUVIDA, 10L, null);

        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.createTicket(dto));
        assertEquals("O solicitante deve ter o papel REQUESTER.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve atribuir técnico ao chamado e transitar automaticamente de CRIADO para ABERTO")
    void assignTechnician_Success() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        assertEquals(TicketStatus.CRIADO, ticket.getStatus());
        TicketAssignTechnicianDTO dto = new TicketAssignTechnicianDTO(2L);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.assignTechnician(1L, dto);

        assertNotNull(response);
        assertEquals(2L, response.getTechnicianId());
        assertEquals("Tecnico Suporte", response.getTechnicianName());
        assertEquals(TicketStatus.ABERTO, response.getStatus());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException ao tentar atribuir usuário sem papel TECHNICIAN")
    void assignTechnician_InvalidRole_ThrowsException() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        User nonTechUser = new User("Outro", "outro@helpdesk.com", "123", UserRole.REQUESTER);
        ReflectionTestUtils.setField(nonTechUser, "id", 5L);
        TicketAssignTechnicianDTO dto = new TicketAssignTechnicianDTO(5L);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(5L)).thenReturn(Optional.of(nonTechUser));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.assignTechnician(1L, dto));
        assertEquals("Apenas usuários com papel TECHNICIAN podem ser atribuídos como técnico.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException ao tentar atribuir técnico a chamado que já tem técnico")
    void assignTechnician_AlreadyAssigned_ThrowsException() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setTechnician(technician);
        TicketAssignTechnicianDTO dto = new TicketAssignTechnicianDTO(3L);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.assignTechnician(1L, dto));
        assertEquals("O chamado já possui um técnico atribuído e não pode ser reatribuído.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve transitar status de ABERTO para EM_ATENDIMENTO")
    void updateTicketStatus_ToEmAtendimento_Success() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.ABERTO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.EM_ATENDIMENTO, null);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.updateTicketStatus(1L, dto);

        assertEquals(TicketStatus.EM_ATENDIMENTO, response.getStatus());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException para transição para RESOLVIDO sem solução")
    void updateTicketStatus_ToResolvido_WithoutSolution_ThrowsException() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.RESOLVIDO, null);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.updateTicketStatus(1L, dto));
        assertEquals("Para transitar o status para RESOLVIDO, o campo solução é obrigatório.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve transitar para RESOLVIDO com sucesso quando solução for informada")
    void updateTicketStatus_ToResolvido_WithSolution_Success() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.RESOLVIDO, "Cabo de rede reconectado");

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.updateTicketStatus(1L, dto);

        assertEquals(TicketStatus.RESOLVIDO, response.getStatus());
        assertEquals("Cabo de rede reconectado", response.getSolution());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException ao transitar para FECHADO se status atual não for RESOLVIDO")
    void updateTicketStatus_ToFechado_WhenCurrentNotResolvido_ThrowsException() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.FECHADO, "Solucao");

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.updateTicketStatus(1L, dto));
        assertEquals("O chamado só pode ser alterado para FECHADO se o status atual for RESOLVIDO.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve transitar para FECHADO com sucesso quando status atual for RESOLVIDO")
    void updateTicketStatus_ToFechado_WhenCurrentIsResolvido_Success() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.RESOLVIDO);
        ticket.setSolution("Solucao prévia");
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.FECHADO, "Solucao prévia");

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.updateTicketStatus(1L, dto);

        assertEquals(TicketStatus.FECHADO, response.getStatus());
    }

    @Test
    @DisplayName("Deve permitir cancelar chamado se estiver com status CRIADO")
    void updateTicketStatus_ToCancelado_WhenCriado_Success() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.CRIADO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.CANCELADO, null);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.updateTicketStatus(1L, dto);

        assertEquals(TicketStatus.CANCELADO, response.getStatus());
    }

    @Test
    @DisplayName("Deve permitir cancelar chamado se estiver ABERTO")
    void updateTicketStatus_ToCancelado_WhenAberto_Success() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.ABERTO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.CANCELADO, null);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponseDTO response = ticketService.updateTicketStatus(1L, dto);

        assertEquals(TicketStatus.CANCELADO, response.getStatus());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException ao tentar cancelar chamado RESOLVIDO ou FECHADO")
    void updateTicketStatus_ToCancelado_WhenResolvido_ThrowsException() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.RESOLVIDO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.CANCELADO, null);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ticketService.updateTicketStatus(1L, dto));
        assertEquals("Não é permitido cancelar um chamado que já está com status RESOLVIDO.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException para transição fora do fluxo permitido")
    void updateTicketStatus_InvalidTransition_ThrowsException() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setStatus(TicketStatus.CRIADO);
        TicketStatusUpdateDTO dto = new TicketStatusUpdateDTO(TicketStatus.RESOLVIDO, "Tentando pular etapas");

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        assertThrows(BusinessRuleException.class, () -> ticketService.updateTicketStatus(1L, dto));
    }

    @Test
    @DisplayName("Deve buscar chamados por solicitante com sucesso")
    void getTicketsByRequester_Success() {
        Ticket ticket = new Ticket("Titulo", "Desc", TicketCategory.DUVIDA, TicketPriority.BAIXA, requester, null);
        ticket.setId(1L);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(ticketRepository.findByRequesterId(1L)).thenReturn(List.of(ticket));

        List<TicketResponseDTO> result = ticketService.getTicketsByRequester(1L);

        assertEquals(1, result.size());
        assertEquals("Titulo", result.get(0).getTitle());
    }
}
