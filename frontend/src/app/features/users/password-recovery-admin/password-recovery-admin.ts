import { ChangeDetectorRef,Component,OnDestroy,OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PasswordRecoveryService,RecoveryRequest,RecoveryAuthorization } from '../../../core/services/password-recovery.service';
import { AuthService } from '../../../core/services/auth.service';
@Component({selector:'app-password-recovery-admin',imports:[RouterLink,FormsModule],templateUrl:'./password-recovery-admin.html',styleUrl:'./password-recovery-admin.css'})
export class PasswordRecoveryAdmin implements OnInit,OnDestroy {
 rows:RecoveryRequest[]=[];filter='PENDING';busy=false;error='';message='';selected:RecoveryRequest|null=null;identityVerified=false;issued:RecoveryAuthorization|null=null;
 constructor(private readonly service:PasswordRecoveryService,private readonly auth:AuthService,private readonly cdr:ChangeDetectorRef){}
 get filtered(){return this.rows.filter(r=>!this.filter||r.status===this.filter);}
 ngOnInit(){this.load();}
 load(){this.service.list().subscribe({next:rows=>{this.rows=rows;this.cdr.markForCheck();},error:e=>this.failure(e)});}
 select(row:RecoveryRequest){if(this.busy)return;this.issued=null;this.selected=row;this.identityVerified=false;this.error='';this.message='';}
 approve(){if(this.busy||!this.selected||!this.identityVerified)return;this.busy=true;this.error='';this.issued=null;
  this.service.approve(this.selected).subscribe({next:authorization=>{this.busy=false;this.selected=null;this.issued=authorization;this.message='Autorização emitida. Entregue por um canal confiável; ela não será exibida novamente.';this.load();this.cdr.markForCheck();},error:e=>this.failure(e)});
 }
 decide(row:RecoveryRequest,action:'reject'|'revoke'){if(this.busy)return;this.busy=true;this.issued=null;this.error='';this.service.decide(row,action).subscribe({next:()=>{this.busy=false;this.selected=null;this.message=action==='reject'?'Solicitação rejeitada.':'Autorização revogada.';this.load();this.cdr.markForCheck();},error:e=>this.failure(e)});}
 private failure(e:HttpErrorResponse){this.busy=false;this.issued=null;this.selected=null;if(e.status===401){this.auth.logout();return;}this.error=e.status===403?'Acesso restrito a administradores.':e.error?.message||'Não foi possível concluir a operação.';if(e.status===409)this.load();this.cdr.markForCheck();}
 status(value:string){const names:Record<string,string>={PENDING:'Pendente',APPROVED:'Autorizada',REJECTED:'Rejeitada',USED:'Utilizada',REVOKED:'Revogada',EXPIRED:'Expirada'};return names[value]||value;}
 date(value:string|null){return value?new Date(value).toLocaleString('pt-BR'):'—';}
 async copy(){if(!this.issued)return;try{await navigator.clipboard.writeText(this.issued.authorization);this.message='Autorização copiada. Entregue somente ao titular verificado.';}catch{this.message='Selecione a autorização e copie manualmente.';}this.cdr.markForCheck();}
 ngOnDestroy(){this.issued=null;this.selected=null;}
}
