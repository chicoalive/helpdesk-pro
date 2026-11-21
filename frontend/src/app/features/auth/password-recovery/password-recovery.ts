import { ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { FormControl,FormGroup,ReactiveFormsModule,Validators } from '@angular/forms';
import { ActivatedRoute,RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { PasswordRecoveryService } from '../../../core/services/password-recovery.service';
@Component({selector:'app-password-recovery',imports:[ReactiveFormsModule,RouterLink],templateUrl:'./password-recovery.html',styleUrls:['../login/login.css','./password-recovery.css']})
export class PasswordRecovery implements OnDestroy {
 readonly resetMode:boolean;
 busy=false;message='';error='';completed=false;
 readonly form=new FormGroup({
  email:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.email,Validators.maxLength(255)]}),
  authorization:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.pattern(/^[A-Za-z0-9_-]{43}$/)]}),
  password:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.minLength(12),(c)=>new TextEncoder().encode(c.value??'').length<=72 && /\p{L}/u.test(c.value??'') && /\p{N}/u.test(c.value??'')?null:{strength:true}]}),
  confirmation:new FormControl('',{nonNullable:true,validators:[Validators.required]})
 },{validators:g=>g.get('password')?.value===g.get('confirmation')?.value?null:{confirmation:true}});
 constructor(private readonly service:PasswordRecoveryService,route:ActivatedRoute,private readonly cdr:ChangeDetectorRef){
  this.resetMode=route.snapshot.data['mode']==='reset';
  if(this.resetMode)this.form.controls.email.disable();
  else{this.form.controls.authorization.disable();this.form.controls.password.disable();this.form.controls.confirmation.disable();}
 }
 submit(){
  if(this.busy||this.completed)return;this.error='';this.message='';
  const values=this.form.getRawValue();
  if(!this.resetMode)this.form.patchValue({email:values.email.trim().toLowerCase()});
  if(this.form.invalid){this.form.markAllAsTouched();return;}
  this.busy=true;
  const op=this.resetMode?this.service.reset(values.authorization,values.password,values.confirmation):this.service.request(this.form.controls.email.value);
  op.subscribe({next:r=>{this.busy=false;this.message=r.message;this.completed=true;if(this.resetMode)localStorage.removeItem('helpdesk_token');this.clearSecrets();this.cdr.markForCheck();},error:(e:HttpErrorResponse)=>{
   this.busy=false;
   this.error=e.status===429?'Aguarde um minuto antes de tentar novamente.':this.resetMode?'Não foi possível redefinir. Verifique a autorização, sua validade e as regras da nova senha.':'Não foi possível registrar a solicitação. Tente novamente mais tarde.';
   this.cdr.markForCheck();
  }});
 }
 private clearSecrets(){this.form.controls.authorization.reset();this.form.controls.password.reset();this.form.controls.confirmation.reset();}
 ngOnDestroy(){this.clearSecrets();}
}
