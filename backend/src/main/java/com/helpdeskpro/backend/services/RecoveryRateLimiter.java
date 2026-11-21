package com.helpdeskpro.backend.services;
import com.helpdeskpro.backend.domain.entities.PasswordRecoveryRate;
import com.helpdeskpro.backend.repositories.PasswordRecoveryRateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
@Service public class RecoveryRateLimiter {
 private final PasswordRecoveryRateRepository rates; private final JdbcTemplate jdbc; private final Clock clock;
 public RecoveryRateLimiter(PasswordRecoveryRateRepository rates,JdbcTemplate jdbc,Clock clock){this.rates=rates;this.jdbc=jdbc;this.clock=clock;}
 public static String hash(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException("Hash unavailable");}}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public boolean allow(String ip,String email,boolean reset){
  jdbc.queryForObject("SELECT id FROM password_recovery_guard WHERE id=1 FOR UPDATE",Integer.class);
  Instant now=clock.instant();
  // Cleanup is bounded by the global request limit and never touches account records.
  jdbc.update("DELETE FROM password_recovery_rate WHERE window_started < ?",java.sql.Timestamp.from(now.minus(Duration.ofDays(1))));
  boolean ipAllowed=consume(hash((reset?"reset-ip:":"request-ip:")+ip),reset?5:20,Duration.ofMinutes(1),now);
  boolean emailAllowed=reset || consume(hash("email:"+email),3,Duration.ofHours(1),now);
  rates.flush();return ipAllowed && emailAllowed;
 }
 private boolean consume(String key,int limit,Duration window,Instant now){
  PasswordRecoveryRate rate=rates.findById(key).orElseGet(PasswordRecoveryRate::new);
  if(rate.key==null || !now.isBefore(rate.windowStarted.plus(window))){rate.key=key;rate.windowStarted=now;rate.attempts=0;}
  if(rate.attempts<limit+1)rate.attempts++;
  rates.save(rate);return rate.attempts<=limit;
 }
}
