package com.helpdeskpro.backend.domain.entities;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="password_recovery_rate")
public class PasswordRecoveryRate {
 @Id @Column(name="bucket_key",length=64) public String key;
 @Column(name="window_started",nullable=false) public Instant windowStarted;
 @Column(nullable=false) public int attempts;
 public PasswordRecoveryRate() {}
}
