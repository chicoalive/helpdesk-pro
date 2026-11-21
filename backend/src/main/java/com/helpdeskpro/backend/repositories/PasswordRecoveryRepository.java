package com.helpdeskpro.backend.repositories;
import com.helpdeskpro.backend.domain.entities.PasswordRecovery;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from PasswordRecovery r where r.id=:id")
 Optional<PasswordRecovery> lockById(@Param("id") Long id);
 Optional<PasswordRecovery> findByTokenHash(String hash);
 List<PasswordRecovery> findByUserIdAndStatusIn(Long id,Collection<String> status);
 List<PasswordRecovery> findTop200ByOrderByRequestedAtDescIdDesc();
 List<PasswordRecovery> findByStatusInOrderByRequestedAtDescIdDesc(Collection<String> statuses);
}
