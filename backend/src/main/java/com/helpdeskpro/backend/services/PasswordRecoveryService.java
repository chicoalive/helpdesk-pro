package com.helpdeskpro.backend.services;
import com.helpdeskpro.backend.domain.*;
import com.helpdeskpro.backend.domain.entities.*;
import com.helpdeskpro.backend.dto.PasswordRecoveryDTOs.*;
import com.helpdeskpro.backend.repositories.*;
import com.helpdeskpro.backend.exceptions.*;
import com.helpdeskpro.backend.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
@Service public class PasswordRecoveryService {
 private final PasswordRecoveryRepository requests;private final UserRepository users;private final UserAuditLogRepository audit;
 private final RecoveryRateLimiter limiter;private final SecurityUtils security;private final EntityManager em;private final PasswordEncoder encoder;private final Clock clock;
 private final org.springframework.transaction.support.TransactionTemplate transactions;
 private static final SecureRandom RANDOM=new SecureRandom();
 public PasswordRecoveryService(PasswordRecoveryRepository requests,UserRepository users,UserAuditLogRepository audit,
  RecoveryRateLimiter limiter,SecurityUtils security,EntityManager em,PasswordEncoder encoder,Clock clock,org.springframework.transaction.PlatformTransactionManager manager){
  this.requests=requests;this.users=users;this.audit=audit;this.limiter=limiter;this.security=security;this.em=em;this.encoder=encoder;this.clock=clock;this.transactions=new org.springframework.transaction.support.TransactionTemplate(manager);
 }
 public void request(String email,String ip){
  if(!limiter.allow(ip,email,false))return;
  transactions.executeWithoutResult(status->createRequest(email));
 }
 private void createRequest(String email){
  User found=users.findByEmailIgnoreCase(email).orElse(null);if(found==null)return;
  User user=users.findByIdForUpdate(found.getId()).orElseThrow();em.refresh(user);if(!user.isEnabled())return;
  var live=requests.findByUserIdAndStatusIn(user.getId(),List.of("PENDING","APPROVED"));
  for(var old:live){
   em.refresh(old);
   boolean expired=old.expiresAt!=null && !clock.instant().isBefore(old.expiresAt);
   if(!expired && old.sessionVersion==user.getSessionVersion())return;
   old.status=expired?"EXPIRED":"REVOKED";old.tokenHash=null;requests.saveAndFlush(old);
   event(null,old,expired?"PASSWORD_RESET_AUTHORIZATION_EXPIRED":"PASSWORD_RESET_AUTHORIZATION_REVOKED");
  }
  PasswordRecovery r=new PasswordRecovery();r.user=user;r.status="PENDING";r.requestedAt=clock.instant();r.sessionVersion=user.getSessionVersion();
  requests.saveAndFlush(r);event(null,r,"PASSWORD_RECOVERY_REQUESTED");
 }
 private User administrator(){
  Long id=security.getCurrentUser().getId();users.lockActiveAdministrators();
  User actor=users.findById(id).orElseThrow(()->new AccessDeniedException("Acesso negado."));em.refresh(actor);
  if(!actor.isEnabled() || actor.getRole()!=UserRole.ADMIN)throw new AccessDeniedException("Apenas administradores podem autorizar recuperações.");return actor;
 }
 private PasswordRecovery lock(Long id){
  var found=requests.findById(id).orElseThrow(()->new UserNotFoundException("Solicitação não encontrada."));
  User user=users.findByIdForUpdate(found.user.getId()).orElseThrow();em.refresh(user);
  var r=requests.lockById(id).orElseThrow();em.refresh(r);return r;
 }
 private void version(PasswordRecovery r,long expected){if(r.version!=expected)throw new OptimisticLockingFailureException("Solicitação alterada por outra operação.");}
 @Transactional public Issued approve(Long id,Decision decision){
  User actor=administrator();var r=lock(id);version(r,decision.expectedVersion());
  if(!Boolean.TRUE.equals(decision.identityVerified()))throw new BusinessRuleException("Confirme a verificação de identidade antes de autorizar.");
  if(!r.status.equals("PENDING"))throw new OptimisticLockingFailureException("Solicitação não está pendente.");
  if(!r.user.isEnabled())throw new BusinessRuleException("Conta inativa. Analise administrativamente antes de uma nova solicitação.");
  if(r.sessionVersion!=r.user.getSessionVersion())throw new BusinessRuleException("A conta foi alterada. Rejeite esta solicitação e peça uma nova.");
  byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String authorization=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);Arrays.fill(bytes,(byte)0);
  r.tokenHash=RecoveryRateLimiter.hash(authorization);r.status="APPROVED";r.approvedBy=actor;r.decidedAt=clock.instant();r.expiresAt=clock.instant().plus(Duration.ofMinutes(15));
  requests.saveAndFlush(r);event(actor,r,"PASSWORD_RECOVERY_APPROVED");event(actor,r,"PASSWORD_RESET_AUTHORIZATION_ISSUED");
  return new Issued(r.id,authorization,r.expiresAt);
 }
 @Transactional public View decide(Long id,Decision decision,boolean revoke){
  User actor=administrator();var r=lock(id);version(r,decision.expectedVersion());
  if(revoke && r.status.equals("REVOKED"))return view(r);
  if(!(revoke?r.status.equals("APPROVED"):r.status.equals("PENDING")))throw new OptimisticLockingFailureException("Estado da solicitação não permite esta operação.");
  r.status=revoke?"REVOKED":"REJECTED";r.tokenHash=null;r.decidedAt=clock.instant();r.approvedBy=actor;requests.saveAndFlush(r);
  event(actor,r,revoke?"PASSWORD_RESET_AUTHORIZATION_REVOKED":"PASSWORD_RECOVERY_REJECTED");return view(r);
 }
 public void reset(Reset body,String ip){
  if(!limiter.allow(ip,"",true))throw new RecoveryRateLimitException();
  transactions.executeWithoutResult(status->consumeAuthorization(body));
 }
 private void consumeAuthorization(Reset body){
  var found=requests.findByTokenHash(RecoveryRateLimiter.hash(body.authorization())).orElse(null);
  if(found==null)throw invalid();
  var r=lock(found.id);
  if(!r.status.equals("APPROVED") || r.tokenHash==null || !r.tokenHash.equals(RecoveryRateLimiter.hash(body.authorization()))
   || r.expiresAt==null || !clock.instant().isBefore(r.expiresAt) || !r.user.isEnabled()
   || r.sessionVersion!=r.user.getSessionVersion())throw invalid();
  r.user.setPassword(encoder.encode(body.password()));r.user.revokeSessions();users.saveAndFlush(r.user);
  r.status="USED";r.tokenHash=null;requests.saveAndFlush(r);event(null,r,"PASSWORD_RESET_COMPLETED");
  // Invalidate any other authorization for this account without altering historical requests.
 }
 private BusinessRuleException invalid(){return new BusinessRuleException("Autorização inválida ou indisponível. Solicite uma nova recuperação.");}
 @Transactional public List<View> list(){
  administrator();Map<Long,PasswordRecovery> records=new LinkedHashMap<>();
  requests.findByStatusInOrderByRequestedAtDescIdDesc(List.of("PENDING","APPROVED")).forEach(r->records.put(r.id,r));
  requests.findTop200ByOrderByRequestedAtDescIdDesc().forEach(r->records.putIfAbsent(r.id,r));
  return records.values().stream().sorted(Comparator.comparing((PasswordRecovery r)->r.requestedAt).thenComparing(r->r.id).reversed()).map(this::view).toList();
 }
 private View view(PasswordRecovery r){String state=r.status;if(state.equals("APPROVED") && !clock.instant().isBefore(r.expiresAt))state="EXPIRED";
  return new View(r.id,r.user.getId(),r.user.getName(),r.user.getEmail(),r.user.getRole().name(),r.user.isEnabled(),state,r.requestedAt,r.decidedAt,r.expiresAt,r.approvedBy==null?null:r.approvedBy.getId(),r.version);}
 private void event(User actor,PasswordRecovery r,String operation){audit.saveAndFlush(UserAuditLog.recovery(actor,r.user,operation,r.id));}
}
