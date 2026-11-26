# Homologação 14.2.3 — Política e medição de SLA

Concluída e aplicada ao ambiente principal em 24/11/2026, após testes PostgreSQL, restauração isolada, builds e inspeção visual. Sem commit ou push. Etapas 14.2.4 e 15 não implementadas.

## Política efetiva, sem novas regras comerciais

| Prioridade | Prazo desde a criação |
| --- | --- |
| ALTA | 4 horas corridas |
| MEDIA | 24 horas corridas |
| BAIXA | 72 horas corridas |

Início: `createdAt` em UTC. Vencimento: início mais a duração da prioridade. No instante exato do vencimento o chamado ainda está no prazo; atraso começa quando a referência é estritamente posterior. AGUARDANDO_USUARIO continua consumindo o prazo normalmente. Nenhum calendário comercial, feriado, pausa, escalonamento, notificação ou reabertura foi introduzido.

A referência para o resultado de cumprimento é a entrada efetiva em RESOLVIDO, coerente com a política anterior que já encerrava o alerta ativo nesse estado. FECHADO preserva o resultado da resolução; não começa outra medição. Repetir RESOLVIDO ou editar solução não altera o instante registrado. Cancelamento não é classificado como cumprimento ou descumprimento.

A prioridade já era determinada pela categoria na criação. Não existe endpoint autorizado para alterá-la depois. Essa regra foi preservada; a tentativa de enviar outra prioridade na abertura não substitui o cálculo do servidor. Não foi criada edição de prioridade nesta entrega. O prazo histórico de uma resolução nova é um snapshot e não muda se a prioridade for alterada futuramente por uma implementação autorizada.

## Diagnóstico e correções

O cálculo 4/24/72 e a comparação estrita já estavam corretos. O defeito era usar exclusivamente `slaBreached=false` para apresentar “Dentro do SLA”: resolvidos, fechados e cancelados também recebiam esse valor, produzindo conclusão falsa de cumprimento.

A política central `TicketSla` distingue seis situações: em andamento no prazo, em andamento atrasado, resolvido no prazo, resolvido fora do prazo, cancelado e sem informação confiável. O booleano `slaBreached` permanece compatível e representa somente alerta ativo; o novo `slaStatus` expressa o resultado completo.

Datas e resultado são calculados no backend com relógio UTC injetável. A criação pelo serviço usa esse relógio e o ciclo de persistência preserva o instante informado pelo próprio serviço. O registro de resolução e o prazo vigente são persistidos na mesma transação do estado, solução e auditoria. Uma falha de auditoria ou conflito de versão desfaz também os novos campos.

Instantes persistidos respeitam a precisão de microssegundos do PostgreSQL. Atraso positivo é exposto em segundos arredondados para cima, evitando mostrar zero quando já existe atraso fracionário. O instante exato não é arredondado na comparação em memória. O alerta ativo é avaliado no momento da consulta (`slaEvaluatedAt`); a interface não introduz relógio local para recalcular a política, polling ou notificações. Recarregar os dados atualiza a consulta. O atraso histórico fica congelado na resolução.

Listagem, detalhes e dashboard usam a mesma política. As projeções leves da listagem carregam os novos instantes sem carregar os bytes dos anexos. Dashboard acrescenta contagens simples por situação de SLA; suas consultas usam uma transação de leitura com snapshot consistente. Nenhum gráfico complexo foi acrescentado.

## Histórico e migração V9

V9 adiciona somente `tickets.resolved_at` e `tickets.sla_resolution_deadline`, ambos timestamp com fuso e nullable. Uma restrição exige o par completo e datas coerentes com a criação. Não há UPDATE de registros antigos, recriação de tabela ou preenchimento retroativo. V1–V8 foram preservadas e seus checksums foram conferidos.

No principal foram encontrados três chamados resolvidos/fechados, cada um com evento de resolução na auditoria, mas sem snapshot do prazo vigente nesse evento. Ausência de evento de prioridade não prova que o valor atual era o mesmo naquele momento. `updatedAt` também não representa necessariamente resolução. Por isso não foi usado para inventar histórico. Os três aparecem como “Sem medição histórica”; o prazo derivado atual é apresentado apenas como referência, acompanhado de explicação nos detalhes. Os sete registros antigos mantiveram os novos campos nulos. Novas resoluções passam a ter medição confiável.

## API e apresentação

Sem novos endpoints. Respostas de chamados acrescentam `slaStatus`, `resolvedAt`, `slaEvaluatedAt` e `slaDelaySeconds`. `slaDeadline` usa o snapshot quando há resolução medida e mantém o cálculo anterior nos demais casos. Campos novos não podem ser definidos pelo solicitante através dos DTOs de abertura ou alteração de estado.

Horários são serializados como instantes UTC e apresentados em português no fuso explícito `America/Sao_Paulo`, identificado como Brasília. Listagem mostra prazo final, situação e atraso; detalhes também mostram o instante da resolução ou da consulta e explicam a insuficiência histórica. Solicitante recebe mensagens compreensíveis sem jargão de SLA. Cancelados e encerrados não são rotulados como atendimento ainda em andamento.

Login, fotografia, logo, efeito de vidro, autenticação, permissões e política de transferências ficaram intactos. Não houve mudança de design ou CSS nesta entrega.

## Testes e evidências reais

- Backend: **454 testes aprovados**, zero falhos, zero erros e zero pulados. Inclui os 425 anteriores, cinco regressões unitárias de SLA e 24 novas execuções PostgreSQL com relógio controlável.
- Frontend: **244 testes aprovados em 24 arquivos**, zero falhos ou pulados. Inclui regressões de detalhes, lista, dashboard, histórico desconhecido, cancelamento e conversão de horário.
- PostgreSQL: criação/prioridades, antes/exatamente/depois do vencimento (microssegundo), resolução e fechamento, cancelamento, espera sem pausa, UTC, concorrência, rollback da auditoria, permissões dos três perfis, vínculos/anexos/comentários e migração V8→V9 idempotente.
- Limite em memória também testado imediatamente antes/depois por nanossegundo. Exemplo controlado: criação 01/01/2026 12:00Z e prioridade ALTA resultam em prazo 16:00Z; resolução exatamente às 16:00Z cumpre o prazo, e às 16:00:00.000001Z não cumpre. O atraso exibido nesse último caso é 1 segundo por arredondamento para cima.
- Exemplo controlado: resolução cinco horas após criação de prioridade ALTA registra 3600 segundos de atraso; fechar 30 dias depois não muda o resultado.
- Duas resoluções simultâneas por HTTP real isolado retornaram **200 e 409**, com uma única auditoria da transição para RESOLVIDO.
- Reinício do backend isolado preservou os fingerprints completos de usuários, chamados, comentários e auditorias. Instante, prazo e atraso histórico continuaram iguais; novo login e correspondência entre API de listagem e dashboard foram confirmados.
- Build backend Java 21/bootJar e imagem de produção aprovados. Build Angular e imagem Nginx aprovados. Permanecem os avisos anteriores de orçamento CSS em detalhes (4,28 kB) e gestão de usuários (4,31 kB), sem impedir o build.
- Navegador em 1440×900 e 390×844: ADMIN viu dashboard/lista e resultados; REQUESTER viu cancelamento e explicação de histórico desconhecido sem controles administrativos; TECHNICIAN resolveu pelo formulário existente um chamado aguardando usuário e confirmou resultado atrasado persistido após recarregar. Capturas de homologação fora do repositório.

## Segurança da aplicação e integridade principal

Backup existente validado por checksum, sem alterar seu diretório. A cópia restaurada já na V8 recebeu a V9: oito tabelas comparadas canonicamente preservaram todas as colunas anteriores, e sete chamados continuaram sem snapshot retroativo. O Administrador Principal autenticou também na cópia restaurada antes da aplicação principal.

Somente imagens do backend e frontend foram atualizadas. PostgreSQL e volume originais preservados. Após a atualização: **5 usuários (4 ativos), 2 registros ADMIN (somente Administrador Principal ativo), 7 chamados, 4 ativos, 21 auditorias históricas de chamados, 1 auditoria administrativa, 1 anexo de 185 bytes e 0 comentários**. Comparação de oito tabelas antes/depois abrangeu valores e relacionamentos, senhas codificadas, versões de sessão e bytes dos anexos, não apenas contagens. A V9 acrescentou campos nulos aos registros existentes sem mudar esses valores.

Credencial DPAPI permaneceu recuperável e com o mesmo checksum. Não foram apagadas, movidas ou modificadas as pastas protegidas de credenciais e backups. Nenhuma credencial foi alterada ou exposta. Administrador Principal: login 200 e funcionalidades administrativas acessíveis; conta antiga: login 401. Seed efetivamente false antes/depois da partida. Backend em 127.0.0.1:8080; frontend em 127.0.0.1:4200; banco sem porta publicada.

Ambiente principal: http://localhost:4200. Prévia fictícia: http://127.0.0.1:4325/tickets/4. Nenhum cadastro, chamado ou teste de alteração foi executado com dados reais do principal.

## Arquivos desta entrega

Backend:

- `domain/TicketSla.java`, `domain/enums/TicketSlaStatus.java`.
- `domain/entities/Ticket.java` e `TicketAuditLog.java`.
- `dto/TicketResponseDTO.java` e `DashboardResponseDTO.java`.
- `repositories/TicketRepository.java`.
- `services/TicketService.java` e `DashboardService.java`.
- `db/migration/V9__ticket_sla_resolution.sql`.
- Novo `integration/TicketSlaPostgresIntegrationTest.java`; atualizados `dto/TicketSlaTest.java`, testes unitários de TicketService/DashboardService e expectativas de migração em AccountMigration/CompanyWorkflow.

Frontend:

- `core/models/ticket.model.ts`, `dashboard.model.ts`, novos `ticket-sla.ts` e `ticket-sla.spec.ts`.
- `features/ticket-detail/ticket-detail.ts`, HTML e testes.
- `features/home/home.ts`, HTML e testes.
- `features/home/dashboard-summary/dashboard-summary.ts`, HTML e testes.

Documentação: este relatório, README, PLANO-14.2 e ROADMAP. As alterações pendentes anteriores da 14.2.1/14.2.2 foram mantidas; a lista inteira do Git inclui trabalho de todas essas entregas, sem commit ou push.

## Decisões comerciais pendentes

Definir posteriormente: calendário comercial/feriados, eventual pausa em espera, escalonamento e notificações, critérios e auditoria para edição de prioridade/prazo, eventual reabertura e como uma nova medição se relacionaria com a original. Qualquer classificação retrospectiva dos registros legados exige validação explícita de evidências e regra aprovada; não se deve preencher datas por aproximação.

Os dados legados desconhecidos ficam separados dos resultados medidos. O novo indicador não calcula porcentagens de cumprimento nem trata desconhecidos ou cancelados como sucesso. Não há pendência técnica bloqueante identificada para esta entrega; os avisos CSS e a política comercial futura permanecem registrados.