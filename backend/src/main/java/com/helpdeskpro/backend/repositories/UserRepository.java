package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
