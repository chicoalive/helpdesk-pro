# Etapa 14.2.1 — Homologação operacional de chamados e permissões

Concluída em 21/11/2026, com correções aplicadas ao ambiente principal depois da validação isolada. Sem commit ou push nesta execução. Etapas 14.2.2–14.2.5 e 15 não implementadas.

## O que já funcionava e foi preservado

Abertura pelo solicitante, categoria/prioridade derivadas, seleção de equipamento em uso, leitura por titular, fila administrativa/técnica, assumir/atribuir, estados de atendimento, solução, comentários, imagem, auditoria e dashboard já existiam. Foram reutilizados. Permaneceram intactos: identidade visual, login, gestão de contas, recuperação de senha, sessões seguras e políticas de SLA.

## Matriz de transições

Esta tabela descreve mudanças de estado efetivas para ADMIN e TECHNICIAN **vinculado ao chamado**. Todas as demais mudanças entre estados diferentes são recusadas com HTTP 400, inclusive reabertura.

| Estado atual | Próximos estados permitidos |
| --- | --- |
| CRIADO | ABERTO, CANCELADO |
| ABERTO | EM_ATENDIMENTO, CANCELADO |
| EM_ATENDIMENTO | AGUARDANDO_USUARIO, RESOLVIDO |
| AGUARDANDO_USUARIO | EM_ATENDIMENTO, RESOLVIDO |
| RESOLVIDO | FECHADO |
| FECHADO | Nenhuma mudança |
| CANCELADO | Nenhuma mudança |

RESOLVIDO e FECHADO exigem solução não vazia nas transições; solução previamente registrada pode ser reutilizada. Solicitação para repetir o estado atual é aceita pelo comportamento existente. Sem alteração efetiva, não gera evento de mudança de status. A API atual permite ao ADMIN/técnico vinculado editar a solução ao repetir um estado final; a interface bloqueia a atualização de estado final. Essa política precisa de decisão explícita, não foi modificada nesta entrega.

REQUESTER pode somente cancelar chamado próprio em CRIADO ou ABERTO. Repetir CANCELADO no próprio registro já cancelado é idempotente. Não pode registrar/alterar solução técnica. Tentar outro estado recebe 403; cancelamento após início do atendimento recebe 400.

Foram executadas **147 combinações de perfil × estado atual × estado solicitado**, em PostgreSQL real, além de testes de vínculo, solução e permissões. A matriz detalhada está em [14.2.1-MATRIZ.csv](14.2.1-MATRIZ.csv).

## Matriz de operações e privacidade

| Operação | ADMIN | TECHNICIAN | REQUESTER |
| --- | --- | --- | --- |
| Abrir chamado | Bloqueado pela regra existente | Bloqueado pela regra existente | Permitido; solicitante é obtido da autenticação |
| Listar/detalhar | Todos | Todos | Apenas próprios |
| Ler por ID de outro solicitante | Permitido | Permitido | 403, inclusive alterando a URL manualmente |
| Atribuir | Técnico ativo e com perfil TECHNICIAN; regras atuais de responsável preservadas | Atribuição inicial permitida pela API; interface oferece assumir para si | 403 |
| Reatribuir responsável ainda ativo e técnico | Bloqueado | Bloqueado | Bloqueado |
| Substituir responsável inativo/que perdeu perfil | Permitido pela regra existente | Bloqueado | Bloqueado |
| Alterar estado/solução | Regras da tabela de estados | Somente se responsável atual | Somente cancelar o próprio, sem solução |
| Ler/escrever comentários | Todos | Todos, mesmo sem ser responsável | Somente próprios chamados |
| Ler imagem | Todos | Todos | Somente próprios chamados |
| Enviar imagem | Bloqueado | Bloqueado | Somente próprio chamado, uma imagem |
| Auditoria de chamados | Permitido | Permitido | 403 |
| Gestão de usuários/recuperações administrativas | Permitido | 403 no backend e rota de usuários bloqueada | Bloqueado |

Contas inativas e JWTs revogados continuam recebendo 401. DTOs não expõem senhas/hashes. Campos não editáveis enviados na abertura (requesterId, technicianId, status, solution e priority) não substituem a identidade ou os valores calculados no servidor. Não existe endpoint de edição geral de chamados que aceite esses campos.

Leitura de comentários e anexos de outro solicitante foi testada diretamente na API, além da negação do detalhe pelo navegador. A criação pelos perfis não solicitantes permanece recusada com o contrato existente de validação de negócio; não foi introduzida nova política de abertura.

## Defeitos reproduzidos e corrigidos

1. **Injeção de solução pelo solicitante:** o PATCH de cancelamento aceitava solution. Regressão reproduziu HTTP 200 indevido e agora exige 403, sem alterar chamado ou auditoria. O Angular nunca envia solução técnica ao cancelar como solicitante, mesmo que já exista uma solução no registro.
2. **Sobrescrita de dados desatualizados:** alterações de estado/solução não tinham versão e podiam perder atualizações. Adicionada versão otimista ao chamado, controle expectedVersion na alteração/atribuição e resposta 409. A interface envia a versão carregada, preserva rascunho ao receber conflito e oferece recarregar explicitamente. O técnico sem vínculo passa a ver controles de estado desabilitados, em coerência com o backend que já negava essa operação.
3. **Aberturas equivalentes simultâneas:** duas requisições podiam passar pela consulta de duplicidade e criar dois incidentes ativos. A verificação/criação agora é serializada pelo registro do solicitante, dentro da transação, com releitura de atividade/perfil/sessão. A regra existente de categorias e equipamento foi preservada; DUVIDA/OUTRO continuam podendo repetir.
4. **Upload concorrente:** a verificação de imagem existente não tinha bloqueio, permitindo sobrescrita na corrida. Upload usa bloqueio do chamado e releitura antes da checagem. Apenas um envio vence; o outro recebe a recusa já existente por imagem presente. A resposta também aguarda o flush, entregando a revisão persistida.
5. **Limite de título:** o banco já limitava a 255 caracteres, mas a API/formulário não o validavam. Título excedente atingia a persistência e retornava HTTP 409 genérico, em vez de erro de validação. Agora recebe validação 400 e é barrado no Angular; 255 caracteres continuam aceitos.
6. **Empate cronológico de comentários:** a ordenação apenas por horário não definia desempate. A consulta mantém createdAt e usa ID como segundo critério, sem modificar dados históricos.

As três primeiras regressões falharam contra o código anterior em PostgreSQL real. Título e revisão do upload também foram reproduzidos antes da correção. As falhas intermediárias de implementação/fixtures foram corrigidas; os números abaixo representam a última execução completa.

## Concorrência e transações

| Cenário | Resultado validado |
| --- | --- |
| Dois técnicos assumem o mesmo chamado | Um 200 e outro 409; um evento de técnico |
| ADMIN muda estado enquanto técnico assume | Um 200 e outro 409, sem sobrescrita silenciosa |
| Duas mudanças de status com mesma revisão | Um 200 e outro 409; auditoria somente da vencedora |
| Cancelamento concorre com início de atendimento | Um 200 e outro 409; estado final consistente |
| Duas aberturas equivalentes | Uma 201 e outra 400 apontando duplicidade; um registro |
| Comentário durante mudança de estado | Ambas as operações válidas persistem; autoria/UTC preservados |
| Dois uploads | Um 200 e outro 400; conteúdo/nome da imagem vencedora preservados |
| Falha real de persistência na auditoria | Rollback de estado, versão e evento |

Além do MockMvc com transações PostgreSQL reais e sem mocks de serviços/repositórios nessa suíte, foram feitas corridas pela rede HTTP em duas requisições assíncronas: **atribuição 200/409** com auditoria única e **abertura 201/400** com apenas um incidente.

expectedVersion é opcional no contrato para compatibilidade com clientes anteriores. O frontend atualizado sempre o envia, detectando também formulários antigos mesmo após outra transação já ter terminado. Sem esse campo, @Version protege atualizações que se sobrepõem na transação; clientes legados devem adotar expectedVersion para detectar alterações desde sua leitura anterior. Não existe retentativa automática que reaplique um rascunho sobre dados novos.

## Migração V7 e preservação

V7__ticket_concurrency.sql adiciona somente tickets.version, bigint NOT NULL DEFAULT 0, com restrição de valor não negativo. V1–V6 não foram alteradas. Projeções de listagem também carregam a versão; DTOs devolvem a versão atual depois do flush.

Antes da aplicação, foi criado backup completo protegido fora do repositório. Sua restauração foi validada em banco separado: V7 aplicada, oito tabelas comparadas integralmente, excluindo somente a coluna nova da comparação de tickets, e os sete chamados preservados com versão inicial zero. Testes também cobrem instalação nova e adoção do esquema legado.

A implantação recriou somente backend/frontend com as imagens homologadas, mantendo o PostgreSQL original e o volume. Configuração efetiva seed=false conferida antes de iniciar. Serviços públicos do ambiente continuam limitados a localhost.

No principal, antes/depois: **5 usuários (4 ativos), 2 registros ADMIN (1 ativo), 7 chamados, 4 ativos, 21 auditorias de chamados, 0 comentários, 1 anexo e 1 evento administrativo**. A comparação incluiu todos os campos históricos e sensíveis em memória, sem expô-los; somente a coluna version foi acrescentada. Nenhum teste cadastrou ou modificou contas/chamados reais. DPAPI permaneceu byte a byte inalterado e utilizável. Administrador Principal: login e operações administrativas 200. Administrador antigo: inativo e login 401.

## Resultados finais

- **Backend: 404 testes aprovados, 0 falhas, 0 erros, 0 pulados.** Inclui os 238 testes do checkpoint e 166 execuções da nova suíte operacional, sendo 147 da matriz.
- **Frontend: 220 testes aprovados em 23 arquivos**, preservando os 214 anteriores e acrescentando regressões de versão, conflito, vínculo, cancelamento e limite do título.
- **Produção:** bootJar/imagem backend e build/imagem Angular-Nginx concluídos. Bundle inicial Angular: 452,73 kB. Mantidos os avisos CSS preexistentes de chamados e usuários, sem ampliar limites.
- **Navegador real:** solicitante abriu incidente de equipamento com imagem, comentou, respondeu solicitação de informações e cancelou outro chamado permitido; técnico assumiu, iniciou, aguardou usuário, retomou, resolveu e fechou; ADMIN atribuiu outro chamado. Bloqueio por ID, rota administrativa para técnico, conflito e recarga foram observados.
- **Visual:** desktop amplo de 1440 px e celular de 390 px; fila, abertura, detalhe, solução, comentários, imagem, auditoria e mensagens verificados. Sem transbordamento do documento nas medidas observadas. Fotografia, logo e identidade aprovadas preservadas.
- **Reinício isolado:** imagens finais e backend/frontend reiniciados; registros, anexos e auditoria comparados idênticos. A sessão administrativa continuou funcional e os dados reapareceram no navegador. O PostgreSQL compartilhado dos laboratórios não foi reiniciado; a validação foi da persistência após reinício dos serviços da aplicação.

## Arquivos alterados

- Backend: Ticket.java; TicketRequestDTO.java; TicketResponseDTO.java; TicketAssignTechnicianDTO.java; TicketStatusUpdateDTO.java; TicketRepository.java; TicketCommentRepository.java; TicketService.java; GlobalExceptionHandler.java; V7__ticket_concurrency.sql.
- Testes backend: TicketOperationsPostgresIntegrationTest.java; TicketServiceTest.java; AccountMigrationPostgresIntegrationTest.java; CompanyWorkflowPostgresIntegrationTest.java.
- Frontend: core/models/ticket.model.ts; core/services/ticket.service.ts e .spec.ts; features/ticket-detail/ticket-detail.ts, .html e .spec.ts; features/tickets/ticket-form/ticket-form.ts, .html e .spec.ts.
- Documentação: este relatório, CSV da matriz, README, ROADMAP e PLANO-14.2.

## Decisões pendentes, sem mudança de política nesta entrega

- Aprovar ou restringir comentários e envio de imagem após encerramento: continuam permitidos pelas regras atuais e foram testados assim.
- Definir edição de solução ao repetir estado final e harmonizar sua política com a interface; reabertura continua proibida.
- Definir atribuição por TECHNICIAN a outro técnico e atribuição/reconfiguração em estados finais. Transferência de técnico ativo continua bloqueada. Tratar na 14.2.2, sem reatribuição automática.
- Calendário útil, pausas e medição histórica de SLA pertencem à 14.2.3. AGUARDANDO_USUARIO não pausa o prazo atual.
- Validação de imagem confia em MIME informado e tamanho; não valida assinatura/decodificação real nem oferece inspeção antimalware. Arquivo fictício não decodificável com MIME de imagem é aceito pelo contrato atual; documentado para 14.2.4. Não foi identificado nesta homologação um mecanismo de execução de conteúdo que exigisse antecipar essa entrega.
- Não existe limite funcional de tamanho de comentário além dos limites técnicos de requisição e armazenamento; definir limite comercial, retenção e política de anexos na entrega apropriada.
- Contingência de um único ADMIN ativo continua sendo a pendência já registrada na 14.1; nenhum mecanismo de recuperação privilegiada foi criado.

O ambiente principal permanece em http://localhost:4200. Capturas e evidências detalhadas da execução ficam fora do Git, no relatório local entregue. Não foram realizados commit, push, exclusão de volume ou avanço para a Etapa 15.
