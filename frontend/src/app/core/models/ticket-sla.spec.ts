import { describe,it,expect } from 'vitest';
import { slaStatus,slaLabel,slaDelay,brazilDate } from './ticket-sla';
import { TicketResponse } from './ticket.model';
const ticket=(values:Partial<TicketResponse>)=>({status:'ABERTO',slaDeadline:'2026-01-01T12:00:00Z',slaBreached:false,...values} as TicketResponse);
describe('SLA presentation',()=>{
 it.each(['RESOLVIDO','FECHADO'] as const)('does not infer compliance for legacy %s',status=>{expect(slaStatus(ticket({status}))).toBe('UNKNOWN');expect(slaLabel(ticket({status}))).toBe('Sem medição histórica');});
 it('does not mark cancellation as within deadline',()=>{expect(slaLabel(ticket({status:'CANCELADO'}))).toBe('Cancelado — não se aplica');});
 it('distinguishes historical delay from an active alert',()=>{const t=ticket({status:'FECHADO',slaStatus:'RESOLVED_OVERDUE',slaBreached:false,slaDelaySeconds:3661});expect(slaLabel(t)).toBe('Resolvido fora do prazo');expect(slaDelay(t)).toBe('1 h 1 min 1 s');});
 it('renders subsecond delay without a misleading zero',()=>{expect(slaDelay(ticket({slaStatus:'IN_PROGRESS_OVERDUE',slaDelaySeconds:1}))).toBe('1 s');});
 it('keeps requester messages understandable',()=>{expect(slaLabel(ticket({slaBreached:true}),true,true)).toBe('Prazo de atendimento ultrapassado');});
 it('does not fabricate missing deadline or delay',()=>{expect(slaLabel(ticket({slaDeadline:null}))).toBe('Prazo indisponível');expect(slaDelay(ticket({slaDelaySeconds:null}))).toBe('');});
 it('formats UTC as Brasilia time and rejects invalid dates',()=>{expect(brazilDate('2026-01-01T12:00:00Z')).toContain('09:00:00');expect(brazilDate('invalid')).toBe('-');});
});
