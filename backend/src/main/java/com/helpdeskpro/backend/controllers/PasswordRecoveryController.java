package com.helpdeskpro.backend.controllers;
import com.helpdeskpro.backend.dto.PasswordRecoveryDTOs.*;
import com.helpdeskpro.backend.services.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController public class PasswordRecoveryController {
 private final PasswordRecoveryService service;
 public PasswordRecoveryController(PasswordRecoveryService service){this.service=service;}
 @PostMapping("/auth/password-recovery") public ResponseEntity<Map<String,String>> request(@Valid @RequestBody Request body,HttpServletRequest request){
  service.request(body.email(),request.getRemoteAddr());return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("message","Se a conta estiver apta, a solicitação estará disponível para análise administrativa. Procure o administrador por um canal confiável."));
 }
 @PostMapping("/auth/password-reset") public ResponseEntity<Map<String,String>> reset(@Valid @RequestBody Reset body,HttpServletRequest request){
  service.reset(body,request.getRemoteAddr());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("message","Senha redefinida. Entre novamente com a nova senha."));
 }
 @GetMapping("/admin/password-recoveries") public ResponseEntity<List<View>> list(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());}
 @PostMapping("/admin/password-recoveries/{id}/approve") public ResponseEntity<Issued> approve(@PathVariable Long id,@Valid @RequestBody Decision body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(service.approve(id,body));}
 @PostMapping("/admin/password-recoveries/{id}/reject") public View reject(@PathVariable Long id,@Valid @RequestBody Decision body){return service.decide(id,body,false);}
 @PostMapping("/admin/password-recoveries/{id}/revoke") public View revoke(@PathVariable Long id,@Valid @RequestBody Decision body){return service.decide(id,body,true);}
}
