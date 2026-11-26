# Homologação 14.2.2 — Atribuição e continuidade do atendimento

Data: 24/11/2026. Etapa 15 suspensa. Sem commit ou push nesta execução.

## Regras definitivas

- ADMIN atribui chamados disponíveis e transfere responsáveis, inclusive quando o anterior foi desativado ou perdeu o perfil de técnico. Destino sempre TECHNICIAN ativo.
- TECHNICIAN somente assume para si um chamado sem responsável. Não atribui outros técnicos nem transfere chamados já atribuídos.
- REQUESTER acompanha o responsável de seus próprios chamados, sem ações de atribuição.
- Atribuição e transferência somente em CRIADO, ABERTO, EM_ATENDIMENTO e AGUARDANDO_USUARIO. RESOLVIDO, FECHADO e CANCELADO bloqueiam a operação.
- A atribuição inicial em CRIADO mantém a abertura automática para ABERTO. Transferência não muda status, solução, prioridade, solicitante, ativo, comentários ou anexo.
- Transferência exige justificativa não vazia de até 2000 caracteres. Não ocorre transferência automática por alteração da conta anterior.

## Contrato e consistência

`PATCH /tickets/{id}/technician`: `technicianId`, `expectedVersion` obrigatório e `justification` obrigatória quando há troca de responsável. Sem versão: HTTP 400; versão desatualizada: HTTP 409; perfil sem autorização: HTTP 403. ADMIN repetindo o mesmo responsável com versão atual não produz alteração nem auditoria falsa.

A transação bloqueia administradores e contas em ordem consistente com a gestão de usuários, revalida atividade/perfil/sessão e bloqueia o chamado. O controle otimista de versão também impede que uma alteração de status sobrescreva uma transferência concorrente. Quem perdeu o vínculo não consegue continuar alterando o status.

Auditoria transacional existente registra chamado, autor, IDs do responsável anterior e novo, justificativa e instante UTC. Falha de auditoria desfaz a operação inteira. A interface apresenta o horário no fuso local; não muda o armazenamento UTC.

V8 adiciona `ticket_audit_logs.justification` nullable e uma restrição de conteúdo/tamanho. Os eventos históricos ficam com valor nulo. V1–V7 não foram editadas. O adaptador de dados de demonstração explícitos foi atualizado para enviar a versão corrente; seed principal continua desabilitado.

## Interface

Detalhes exibem responsável atual, lista de técnicos ativos por nome, campo de justificativa, Salvar transferência e Cancelar transferência. O formulário não usa entrada manual de IDs. Conflito mantém o texto local e exige recarga explícita antes de nova tentativa. Erro ao carregar técnicos tem mensagem própria. Mudança de status fica bloqueada enquanto a transferência está sendo editada.

Login, fotografia, marca e autenticação preservados. Ajuste CSS limitado à largura do novo campo de justificativa em celular.

## Testes automatizados

- Backend: 425 aprovados, zero falhas e zero pulados, incluindo PostgreSQL real isolado e as 147 combinações de estados/perfis da 14.2.1.
- Frontend: 229 aprovados em 23 arquivos, zero falhas e zero pulados.
- Novos 21 casos PostgreSQL cobrem atribuição ADMIN, autoatribuição, duas tentativas simultâneas, transferência auditada, justificativa inválida, tentativas indevidas dos perfis, técnico inelegível, substituição de responsável inativo/sem perfil, versão ausente/desatualizada, duas transferências concorrentes, perda de vínculo, preservação histórica, dashboard/lista, corrida com status, rollback de auditoria, três estados finais, repetição idempotente e desativação/mudança de perfil concorrentes do destino.
- Corridas confirmam uma operação vencedora e rejeição da versão vencida com 409, sem duplicar a transferência auditada.
- Builds de produção backend (Java 21/bootJar) e frontend (Angular/Nginx) aprovados. Angular mantém avisos de orçamento CSS em detalhes do chamado e gestão de usuários; não impedem o build.

## Validação operacional isolada

Backup principal protegido fora do Git, checksum confirmado. Restaurado em outro banco PostgreSQL e atualizado para V8: oito tabelas comparadas canonicamente antes/depois, com os registros existentes preservados. O campo novo foi desconsiderado apenas na comparação com o esquema anterior.

Empresa fictícia em outro banco, seed desabilitado e serviços em loopback. Navegador: ADMIN atribuiu e transferiu com justificativa auditada, cancelou uma edição e recebeu conflito após alteração por outra sessão; TECHNICIAN assumiu chamado disponível e ficou impedido de alterar o chamado transferido; REQUESTER viu o responsável sem controles administrativos ou auditoria interna. Conferência em desktop 1440×900 e celular 390×844.

## Arquivos desta entrega

Backend: `TicketService`, `TicketAssignTechnicianDTO`, `TicketAuditLog`, `TicketAuditLogResponseDTO`, `DemoDataInitializer`, V8, novo `TicketTransferPostgresIntegrationTest` e adaptações de regressão em `TicketServiceTest`, `HelpDeskApiIntegrationTest`, `TicketAuditIntegrationTest`, `AccountMigrationPostgresIntegrationTest`, `CompanyWorkflowPostgresIntegrationTest`, `UserEditingPostgresIntegrationTest` e `TicketOperationsPostgresIntegrationTest`.

Frontend: modelos de chamado/auditoria, `TicketService` e testes, `TicketDetail` (TypeScript, HTML, CSS e testes). Documentação: este relatório, README, plano 14.2 e roadmap. Demais alterações pendentes da 14.2.1 foram preservadas.

## Limites e próximas entregas

Não implementados SLA, ciclo de vida de ativos, política nova de anexos ou Etapa 15. Transferência em estado resolvido/fechado/cancelado permanece proibida. Existe apenas um administrador ativo no principal; a continuidade administrativa já documentada continua necessária para uso comercial. Nenhuma credencial real foi alterada.
## Aplicação no ambiente principal — resultado real

Aplicadas as imagens `helpdesk-pro-14-2-2:local` e `meu-assistente-ti-frontend:14-2-2` somente após os testes e a restauração isolada. PostgreSQL original saudável, sem substituição do contêiner, sem recriação de volume. Backend em 127.0.0.1:8080, frontend em 127.0.0.1:4200, banco sem porta publicada. Configuração efetiva do seed confirmada como false antes e depois da partida.

Flyway V1–V8 conferidas, incluindo os checksums das migrações anteriores. Contagens reais depois da atualização: 5 usuários (4 ativos), 2 registros ADMIN (1 ativo), 7 chamados, 4 ativos, 21 eventos de auditoria de chamados, 0 comentários, 1 anexo de 185 bytes e 1 evento administrativo. Os 21 eventos anteriores permanecem com justificativa nula. Comparação canônica de oito tabelas antes/depois confirmou os valores anteriores, incluindo senhas codificadas, sessões, anexos e relacionamentos. Nenhum usuário ou chamado fictício foi criado no principal.

Login do Administrador Principal: HTTP 200; listagem administrativa e dashboard: HTTP 200; administrador antigo inativo: HTTP 401. Arquivo DPAPI sem alteração e recuperável; backups com checksum conferido. Frontend e proxy `/api` funcionais. Identidade visual e login sem alterações nesta entrega.

Concorrência adicional na API HTTP real isolada: duas autoatribuições simultâneas retornaram 200 e 409, com apenas um evento de atribuição. Reinício do backend isolado preservou integralmente usuários, chamados, comentários e auditoria; novo login validado após a partida.

Prévia fictícia local: http://127.0.0.1:4324/tickets/1. Ambiente principal: http://localhost:4200. Capturas desktop/celular mantidas fora do repositório. Alterações anteriores da 14.2.1 continuam pendentes e preservadas. HEAD não foi alterado; nenhum commit ou push realizado.