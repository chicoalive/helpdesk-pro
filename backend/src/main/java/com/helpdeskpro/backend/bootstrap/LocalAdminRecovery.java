package com.helpdeskpro.backend.bootstrap;

import com.helpdeskpro.backend.domain.UserRole;
import com.helpdeskpro.backend.dto.PasswordRecoveryDTOs.Reset;
import com.helpdeskpro.backend.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.List;

@Service
public class LocalAdminRecovery {
    private final JdbcTemplate jdbc; private final UserRepository users; private final PasswordRecoveryRepository recoveries;
    private final PasswordEncoder encoder; private final EntityManager em; private final Validator validator;
    private final Clock clock; private final TransactionTemplate transactions;
    public LocalAdminRecovery(JdbcTemplate jdbc,UserRepository users,PasswordRecoveryRepository recoveries,PasswordEncoder encoder,
            EntityManager em,Validator validator,Clock clock,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.users=users;this.recoveries=recoveries;this.encoder=encoder;this.em=em;this.validator=validator;this.clock=clock;this.transactions=new TransactionTemplate(manager);
    }
    public void recover(MaintenanceAuthorization.Verified request) {
        try {
            transactions.executeWithoutResult(status -> {
                if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(1425001)",Boolean.class)))throw new IllegalStateException("BUSY");
                jdbc.execute("SET LOCAL lock_timeout='5s'");
                // Same account lock order as normal administrative operations, plus exclusive table protection.
                jdbc.execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
                users.lockActiveAdministrators();
                var user=users.findByIdForUpdate(request.userId).orElseThrow(()->new IllegalStateException("TARGET"));em.refresh(user);
                if(!user.isEnabled()||user.getRole()!=UserRole.ADMIN||!user.getEmail().equals(request.email)
                    ||users.findByEmailIgnoreCase(request.email).filter(u->u.getId().equals(request.userId)).isEmpty())throw new IllegalStateException("TARGET");
                if(user.getSessionVersion()!=request.sessionVersion||user.getEditVersion()!=request.editVersion)throw new IllegalStateException("STALE");
                if(!MaintenanceSnapshot.fingerprint(jdbc).equals(request.fingerprint))throw new IllegalStateException("BACKUP_STALE");
                if(!validator.validate(new Reset("A".repeat(43),request.password,request.password)).isEmpty())throw new IllegalStateException("PASSWORD_POLICY");
                if(jdbc.queryForObject("SELECT count(*) FROM maintenance_audit_logs WHERE request_id=?",Long.class,request.requestId)!=0)throw new IllegalStateException("REPLAY");
                user.setPassword(encoder.encode(request.password));user.revokeSessions();users.saveAndFlush(user);
                for(var recovery:recoveries.findByUserIdAndStatusIn(user.getId(),List.of("PENDING","APPROVED"))){recovery.status="REVOKED";recovery.tokenHash=null;recoveries.saveAndFlush(recovery);}
                audit(request,"SUCCESS","RECOVERED",user.getId());
            });
        }catch(RuntimeException failure){
            try { transactions.executeWithoutResult(status -> audit(request,"FAILED","RECOVERY_REFUSED",users.existsById(request.userId)?request.userId:null)); }
            catch(RuntimeException ignored){/* The caller still receives failure; never claim successful recovery. */}
            throw new IllegalStateException("Recuperação não concluída. Nenhuma senha foi confirmada; consulte a auditoria local e gere novo backup antes de tentar novamente.");
        }
    }
    private void audit(MaintenanceAuthorization.Verified r,String result,String code,Long id){jdbc.update("INSERT INTO maintenance_audit_logs(request_id,operator_sid,operator_name,target_user_id,reason,result,result_code,backup_sha256,occurred_at) VALUES(?,?,?,?,?,?,?,?,?)",r.requestId,r.operatorSid,r.operatorName,id,r.reason,result,code,r.backupHash,java.sql.Timestamp.from(clock.instant()));}
}
