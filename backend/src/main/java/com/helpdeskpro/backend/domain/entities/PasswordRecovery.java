package com.helpdeskpro.backend.domain.entities;
import com.helpdeskpro.backend.domain.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="password_recovery_requests")
public class PasswordRecovery {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) public User user;
 @Column(name="requested_at",nullable=false) public Instant requestedAt;
 @Column(nullable=false,length=20) public String status;
 @Column(name="session_version",nullable=false) public long sessionVersion;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="approved_by_id") public User approvedBy;
 @Column(name="decided_at") public Instant decidedAt;
 @JsonIgnore @Column(name="token_hash",length=64,unique=true) public String tokenHash;
 @Column(name="expires_at") public Instant expiresAt;
 @Version @Column(nullable=false) public long version;
 public PasswordRecovery() {}
 @Override public String toString(){return "PasswordRecovery[protected]";}
}
