package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.User;
import com.helpdeskpro.backend.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = com.helpdeskpro.backend.domain.UserRole.ADMIN and u.active = true order by u.id")
    java.util.List<User> lockActiveAdministrators();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    long countByRoleAndActiveTrue(UserRole role);

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByRole(UserRole role);

    @Query("select count(u) > 0 from User u where lower(trim(u.email)) = :email and u.id <> :id")
    boolean existsByNormalizedEmailExcludingId(@Param("email") String email, @Param("id") Long id);

    @Query("select count(u) > 0 from User u where lower(trim(u.email)) = :email")
    boolean existsByNormalizedEmail(@Param("email") String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update User u set u.sessionVersion = u.sessionVersion + 1 where u.id = :id")
    int revokeSessionsById(@Param("id") Long id);
}
