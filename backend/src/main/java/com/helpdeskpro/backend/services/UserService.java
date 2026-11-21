package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.domain.entities.UserAuditLog;
import com.helpdeskpro.backend.domain.entities.UserAuditChange;
import com.helpdeskpro.backend.dto.UserUpdateRequestDTO;
import com.helpdeskpro.backend.exceptions.UserNotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
import java.util.LinkedHashMap;
import java.util.Map;
import com.helpdeskpro.backend.dto.UserCreateRequestDTO;
import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import com.helpdeskpro.backend.repositories.UserAuditLogRepository;
import com.helpdeskpro.backend.repositories.UserRepository;
import com.helpdeskpro.backend.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuditLogRepository auditRepository;
    private final SecurityUtils securityUtils;
    private final EntityManager entityManager;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       UserAuditLogRepository auditRepository, SecurityUtils securityUtils, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRepository = auditRepository;
        this.securityUtils = securityUtils;
        this.entityManager = entityManager;
    }

    @Transactional
    public User createUser(UserCreateRequestDTO request) {
        User actor = currentAdministrator();
        if (userRepository.existsByNormalizedEmail(request.email())) {
            throw new BusinessRuleException("Já existe um usuário cadastrado com este e-mail.");
        }
        User created = userRepository.saveAndFlush(new User(request.name(), request.email(),
                passwordEncoder.encode(request.password()), request.role()));
        auditRepository.saveAndFlush(new UserAuditLog(actor, created));
        return created;
    }

    private User currentAdministrator() {
        Long actorId = securityUtils.getCurrentUser().getId();
        // Every administrative write takes the same ordered locks.
        userRepository.lockActiveAdministrators();
        User actor = userRepository.findById(actorId).orElseThrow(
                () -> new AccessDeniedException("Administrador não disponível."));
        entityManager.refresh(actor);
        if (!actor.isEnabled() || actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Apenas administradores podem gerenciar usuários.");
        }
        return actor;
    }

    @Transactional
    public User updateUser(Long id, UserUpdateRequestDTO request) {
        User actor = currentAdministrator();
        User target = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado."));
        entityManager.refresh(target);
        if (target.getEditVersion() != request.expectedVersion()) {
            throw new OptimisticLockingFailureException("Dados alterados por outra operação.");
        }
        if (userRepository.existsByNormalizedEmailExcludingId(request.email(), id)) {
            throw new BusinessRuleException("Já existe um usuário cadastrado com este e-mail.");
        }
        if (target.isEnabled() && target.getRole() == UserRole.ADMIN && request.role() != UserRole.ADMIN
                && userRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessRuleException("O último administrador ativo não pode ser rebaixado.");
        }
        Map<String, UserAuditChange> changes = new LinkedHashMap<>();
        if (!target.getName().equals(request.name())) {
            changes.put("name", new UserAuditChange(target.getName(), request.name()));
        }
        if (!target.getEmail().equals(request.email())) {
            changes.put("email", new UserAuditChange(target.getEmail(), request.email()));
        }
        if (target.getRole() != request.role()) {
            changes.put("role", new UserAuditChange(target.getRole().name(), request.role().name()));
        }
        if (changes.isEmpty()) return target;
        target.setName(request.name());
        target.setEmail(request.email());
        target.setRole(request.role());
        if (changes.containsKey("email") || changes.containsKey("role")) target.revokeSessions();
        userRepository.saveAndFlush(target);
        auditRepository.saveAndFlush(UserAuditLog.updated(actor, target, changes));
        return target;
    }

    @Transactional
    public User changeState(Long id, com.helpdeskpro.backend.dto.UserStateRequestDTO request) {
        User actor = currentAdministrator();
        User target = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado."));
        entityManager.refresh(target);
        if (target.getEditVersion() != request.expectedVersion()) {
            throw new OptimisticLockingFailureException("Dados alterados por outra operação.");
        }
        if (!request.active() && actor.getId().equals(target.getId())) {
            throw new BusinessRuleException("Não é permitido desativar a própria conta.");
        }
        if (target.isEnabled() == request.active()) return target;
        if (!request.active() && target.getRole() == UserRole.ADMIN
                && userRepository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessRuleException("O último administrador ativo não pode ser desativado.");
        }
        boolean previous = target.isEnabled();
        target.setActive(request.active());
        userRepository.saveAndFlush(target);
        auditRepository.saveAndFlush(UserAuditLog.stateChanged(actor, target, previous));
        return target;
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
