package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import com.helpdeskpro.backend.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(User user) {
        if (user == null) {
            throw new BusinessRuleException("Os dados do usuário são obrigatórios.");
        }
        String name = user.getName() != null ? user.getName().trim() : "";
        String email = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : "";
        String rawPassword = user.getPassword();

        if (name.isEmpty()) {
            throw new BusinessRuleException("O nome é obrigatório.");
        }
        if (email.isEmpty()) {
            throw new BusinessRuleException("O e-mail é obrigatório.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessRuleException("A senha é obrigatória.");
        }
        if (user.getRole() == null) {
            throw new BusinessRuleException("O perfil do usuário é obrigatório.");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("Já existe um usuário cadastrado com este e-mail.");
        }

        User newUser = new User(
                name,
                email,
                passwordEncoder.encode(rawPassword),
                user.getRole()
        );
        return userRepository.save(newUser);
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
