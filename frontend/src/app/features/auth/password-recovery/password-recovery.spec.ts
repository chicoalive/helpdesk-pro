import {TestBed,ComponentFixture} from '@angular/core/testing';
import {ActivatedRoute,provideRouter} from '@angular/router';
import {of,Subject,throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {vi} from 'vitest';
import {PasswordRecovery} from './password-recovery';
import {PasswordRecoveryService} from '../../../core/services/password-recovery.service';
describe('Public password recovery',()=>{
 let component:PasswordRecovery;let fixture:ComponentFixture<PasswordRecovery>;
 let service:{request:ReturnType<typeof vi.fn>;reset:ReturnType<typeof vi.fn>};
 async function setup(reset=false){service={request:vi.fn(),reset:vi.fn()};await TestBed.configureTestingModule({imports:[PasswordRecovery],providers:[provideRouter([]),{provide:ActivatedRoute,useValue:{snapshot:{data:{mode:reset?'reset':''}}}},{provide:PasswordRecoveryService,useValue:service}]}).compileComponents();fixture=TestBed.createComponent(PasswordRecovery);component=fixture.componentInstance;fixture.detectChanges();}
 it('validates e-mail and displays only the generic response',async()=>{await setup();component.submit();expect(service.request).not.toHaveBeenCalled();component.form.controls.email.setValue(' User@Empresa.test ');service.request.mockReturnValue(of({message:'Se a conta estiver apta, procure o administrador.'}));component.submit();expect(service.request).toHaveBeenCalledWith('user@empresa.test');expect(component.message).toContain('Se a conta');expect(component.completed).toBe(true);});
 it('blocks duplicate submissions',async()=>{await setup();component.form.controls.email.setValue('user@empresa.test');service.request.mockReturnValue(new Subject());component.submit();component.submit();expect(service.request).toHaveBeenCalledTimes(1);});
 it('validates strength and confirmation before reset',async()=>{await setup(true);component.form.patchValue({authorization:'A'.repeat(43),password:'short',confirmation:'short'});component.submit();expect(service.reset).not.toHaveBeenCalled();component.form.patchValue({password:'StrongPassword123',confirmation:'different'});component.submit();expect(service.reset).not.toHaveBeenCalled();});
 it('clears secrets after successful reset and on destroy',async()=>{await setup(true);component.form.patchValue({authorization:'A'.repeat(43),password:'StrongPassword123',confirmation:'StrongPassword123'});localStorage.setItem('helpdesk_token','old-session');service.reset.mockReturnValue(of({message:'Senha redefinida.'}));component.submit();expect(localStorage.getItem('helpdesk_token')).toBeNull();expect(service.reset).toHaveBeenCalledOnce();expect(component.form.controls.password.value).toBe('');expect(component.form.controls.authorization.value).toBe('');component.form.controls.password.setValue('secret');component.ngOnDestroy();expect(component.form.controls.password.value).toBe('');});
 it('renders rate-limit feedback without revealing account state',async()=>{await setup(true);component.form.patchValue({authorization:'A'.repeat(43),password:'StrongPassword123',confirmation:'StrongPassword123'});service.reset.mockReturnValue(throwError(()=>new HttpErrorResponse({status:429})));component.submit();expect(component.error).toContain('Aguarde');expect(component.busy).toBe(false);});
});
