package com.helpdeskpro.backend.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
public final class PasswordRecoveryDTOs {
 private PasswordRecoveryDTOs(){}
 public record Request(@NotBlank @Email @Size(max=255) String email){
  public Request{email=email==null?null:email.strip().toLowerCase(Locale.ROOT);}
  @Override public String toString(){return "RecoveryRequest[protected]";}
 }
 public record Decision(@NotNull @PositiveOrZero Long expectedVersion,Boolean identityVerified){}
 public record Reset(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String authorization,
  @NotBlank @Size(min=12,max=72) String password,@NotBlank String confirmation){
  @JsonIgnore @AssertTrue(message="Use ao menos 12 caracteres, letras e números, até 72 bytes em UTF-8.")
  public boolean isStrongPassword(){return password==null || (password.getBytes(StandardCharsets.UTF_8).length<=72 && password.codePoints().anyMatch(Character::isLetter) && password.codePoints().anyMatch(Character::isDigit));}
  @JsonIgnore @AssertTrue(message="As senhas não coincidem.")
  public boolean isConfirmationValid(){return password==null || password.equals(confirmation);}
  @Override public String toString(){return "PasswordReset[protected]";}
 }
 public record View(Long id,Long userId,String name,String email,String role,boolean active,String status,
  Instant requestedAt,Instant decidedAt,Instant expiresAt,Long approvedById,long version){}
 public record Issued(Long requestId,String authorization,Instant expiresAt){
  @Override public String toString(){return "ResetAuthorization[protected]";}
 }
}
