# Homologação 14.2.4 — Ativos e anexos

Implementação e aplicação local realizadas em 24/11/2026; retomada, revisão e publicação preparadas em 26/11/2026. As evidências abaixo distinguem a homologação funcional da reconferência posterior. Etapas 14.2.5 e 15 não foram implementadas.

## Estado e escopo

Reutilizados o cadastro de equipamentos, a imagem única por chamado, a auditoria transacional e as permissões existentes. Preservados login, marca, fotografia, estilos aprovados, contas, senhas, JWT, chamados, atribuições e SLA das entregas anteriores. Nenhum equipamento, usuário ou chamado fictício foi criado no principal.

Na retomada de 26/11, Docker Desktop e os três serviços estavam parados. Foram iniciados usando o mesmo projeto Compose e override operacional local, com as imagens 14.2.4 já homologadas. Seed=false foi conferido na configuração efetiva antes do backend. PostgreSQL e seu volume foram reutilizados. Backend em 127.0.0.1:8080, frontend em 127.0.0.1:4200 e banco sem porta publicada.

## Problemas encontrados e correções

- Código de ativo não tinha unicidade normalizada no banco; cadastro agora remove espaços externos e converte para maiúsculas com Locale.ROOT. Índice único em upper(btrim(code)) impede duplicação por caixa/espaços. Código, tipo e descrição respeitam o limite de 255 caracteres; descrição vazia vira null. Valores legados não foram renomeados em massa.
- Edição não detectava formulários antigos. Ativo tem versão; PUT e PATCH de situação exigem expectedVersion. Ausência recebe 400 e versão antiga recebe 409. Não há reaplicação automática do rascunho.
- Não havia trilha própria das alterações patrimoniais. Cadastro e edição efetiva agora gravam autor, ID do ativo, operação, instante UTC e campos anterior/novo na mesma transação. Falha de auditoria desfaz a alteração. Repetição sem mudança não incrementa versão nem gera evento fictício.
- Exclusão física foi bloqueada, inclusive para equipamento sem chamado. A resposta orienta usar Inativo ou Descartado, preservando os registros e seus vínculos.
- Elegibilidade do equipamento era consultada antes do bloqueio. A abertura de incidente relê e bloqueia o ativo dentro da transação: somente Em uso pode ser associado. Uma corrida com mudança de situação falha de forma segura, sem criar o incidente com dados obsoletos.
- Upload confiava no MIME declarado. Agora verifica assinatura, estrutura, integridade e decodificação efetiva antes de persistir. Download também verifica o conteúdo e informa MIME determinado pelo servidor.
- Nomes de arquivos não eram suficientemente protegidos. Upload recusa caminhos, controles e nomes acima de 255 caracteres; download usa nome seguro gerado a partir do ID do chamado.
- Fixtures antigas eram bytes fictícios ou PNG inválido; foram substituídas por imagens geradas pelos codecs do JDK. O mapeamento binário foi explicitado como bytea, já existente no PostgreSQL, evitando limite implícito de 255 bytes nos testes H2 com JPEG real.
- Empates na ordenação da auditoria de chamados foram corrigidos com ID como desempate e relógio consistente na atribuição. Nenhum evento histórico foi alterado.

## Regras mantidas e permissões

| Operação | ADMIN | TECHNICIAN | REQUESTER |
| --- | --- | --- | --- |
| Consultar inventário e equipamento por ID | Sim | Sim | Não, 403 |
| Consultar equipamentos disponíveis para abrir incidente | Sim | Sim | Sim, apenas Em uso, sem descrição patrimonial |
| Cadastrar/editar equipamento ou situação | Sim | Não, 403 | Não, 403 |
| Ler auditoria patrimonial | Sim | Não, 403 | Não, 403 |
| Excluir fisicamente | Bloqueado por regra de negócio | 403 | 403 |
| Enviar imagem | Não | Não | Apenas chamado próprio, uma imagem |
| Consultar chamado e imagem | Todos | Todos | Apenas próprios; URL direta de terceiro também recebe 403 |

Estados internos preservados: IN_USE, UNDER_MAINTENANCE, INACTIVE e DISCARDED; apresentados como Em uso, Em manutenção, Inativo e Descartado. A mudança de situação mantém chamados, comentários, imagens e histórico. A regra anterior permite manutenção/descarte com chamado aberto; não foi criada proibição comercial nova. O formulário pede confirmação ao retirar de uso e informa o efeito sobre novos incidentes. O retorno a Em uso continua permitido pela regra existente.

Escritas administrativas relêem atividade, perfil e versão de sessão do autor após bloqueio, seguindo a ordem de bloqueios da gestão de contas. Autorização HTTP e propriedade do chamado continuam obrigatórias no backend.

## Imagens e limites efetivos

- Uma imagem PNG estática ou JPEG por chamado, até **2.097.152 bytes (2 MiB, apresentado como 2 MB na interface)**. Arquivo exatamente no limite foi aceito e recuperado intacto; um byte acima foi recusado.
- Máximo de 8192 pixels por lado e 16.777.216 pixels totais, verificados antes da decodificação completa. Até dois decodificadores simultâneos; saturação devolve mensagem de tentativa posterior.
- Leitura limitada mesmo quando o tamanho informado pelo arquivo é falso. MIME informado, extensão e conteúdo precisam concordar.
- PNG: estrutura de chunks, limites, CRC, cabeçalho, sequência de dados e fim verificados; extensões de animação são recusadas. JPEG: segmentos, dimensões, varredura e fim verificados, incluindo suporte a JPEG progressivo.
- Arquivos truncados, corrompidos, conteúdo adicional após o fim, pixels inválidos e dimensões excessivas são recusados. ImageIO usa fluxo em memória, ignora metadados na leitura e recusa avisos de decodificação.
- Bytes originais são preservados; não há recodificação automática, serviço externo, dependência nova ou varredura antivírus.
- multipart.max-file-size permanece 2MB. O envelope multipart admite 3MB para comportar uma imagem de 2 MiB e seus cabeçalhos; isso não amplia o limite do arquivo.
- Resposta de imagem tem Content-Type efetivo, Content-Length, X-Content-Type-Options: nosniff, Content-Security-Policy restritiva e Cache-Control: no-store.
- Bloqueio do chamado impede que dois uploads sobrescrevam a imagem. Acesso é autorizado antes de validar/devolver o conteúdo.

O PNG histórico de 185 bytes foi decodificado e servido com HTTP 200 tanto na cópia restaurada quanto no principal, sem alteração dos bytes, do nome armazenado ou dos vínculos. Arquivo legado incompatível deve ser investigado individualmente; não há exclusão ou conversão em massa.

## Migração e preservação

V10__asset_integrity_and_audit.sql acrescenta assets.version NOT NULL DEFAULT 0, restrição de valor não negativo, índice de código normalizado, asset_audit_logs e asset_audit_changes com chaves estrangeiras. V1–V9 não foram reescritas e seus checksums aplicados foram conferidos.

A migração interrompe com erro genérico se códigos antigos colidirem após normalização. Teste PostgreSQL confirmou rollback e preservação dos códigos; não há renomeação, fusão ou remoção automática. Migração de V9 para V10 foi ensaiada antes da aplicação principal em cópia isolada restaurada do backup validado. Oito tabelas históricas foram comparadas por conteúdo e relacionamentos. Os quatro ativos receberam versão zero e nenhum evento patrimonial foi inventado para operações antigas.

Na retomada de 26/11 foi criado outro backup completo protegido fora do Git e restaurado em banco PostgreSQL separado. **As 14 tabelas públicas, inclusive Flyway, recuperação e auditorias, foram comparadas integralmente e permaneceram idênticas.** Backup com SHA-256 verificado e acesso restrito ao usuário Windows e SYSTEM. Backups anteriores e credencial DPAPI permaneceram inalterados. O caminho exato da cópia é mantido no registro operacional privado da instalação, em D:\Estudo\Backups\helpdesk-pro.

## Testes e homologação

### Suítes e builds

- Backend completo: **502 testes aprovados, zero falhas, erros ou pulados**, com PostgreSQL real isolado, H2 e testes unitários. Inclui 40 execuções da nova integração de ativos/anexos e oito testes adicionais do validador de imagens, além das regressões anteriores.
- Frontend completo: **250 testes aprovados em 24 arquivos**, incluindo validação, versão, confirmação de situação, auditoria, duplicidade, conflito e preservação de rascunho.
- Builds de produção backend bootJar e Angular/Nginx concluídos. Permanecem dois avisos CSS anteriores: detalhe de chamado (4,28 kB) e gestão de usuários (4,31 kB). Nenhum orçamento foi aumentado para ocultá-los.
- Na primeira repetição de 26/11, testes PostgreSQL falharam porque a porta dinâmica do laboratório havia mudado após reiniciar Docker. O endereço isolado foi corrigido e a suíte completa foi repetida. Não houve alteração no banco principal para resolver essa falha.

### PostgreSQL, HTTP e reinício

| Cenário | Resultado |
| --- | --- |
| Dois administradores cadastram o mesmo código | 201 e 409; um ativo e um evento |
| Dois administradores editam a mesma revisão | 200 e 409; somente alteração vencedora auditada |
| Edição sem mudança real | Mesma versão, sem novo evento |
| Falha na auditoria de cadastro/edição | Rollback de ativo, versão e evento |
| Mudança de situação de ativo vinculado | Histórico, chamado, comentário e imagem preservados |
| Abertura disputa ativo que saiu de uso | Recusa segura, sem novo incidente inconsistente |
| Dois uploads no mesmo chamado | 200 e 400; bytes do vencedor preservados |
| PNG/JPEG reais no JRE da imagem de produção | Upload/download íntegros |
| PNG de exatamente 2 MiB | Aceito por HTTP multipart e devolvido com os mesmos bytes |
| Outro solicitante consulta imagem por ID | 403, sem exposição do conteúdo |
| Reinício da aplicação isolada | Login, registros, imagens e auditorias preservados |

Os testes de persistência após reinício abrangeram os serviços da aplicação; não foi simulado desligamento abrupto do servidor PostgreSQL ou recuperação de desastre. A restauração integral do backup foi validada separadamente.

### Navegador real

Em laboratório com dados fictícios, foram executados cadastro com normalização, edição, confirmação de manutenção/descarte, leitura da auditoria e conflito 409 com rascunho preservado. O botão Recarregar ativo somente substitui os dados após ação explícita. Técnico consultou inventário sem controles administrativos. Solicitante teve a rota de inventário bloqueada, selecionou apenas equipamento elegível e abriu chamado com JPEG real. Outro solicitante recebeu recusa por URL direta e não visualizou a imagem.

Desktop 1440×900 e celular 390×844 foram inspecionados. Formulário, mensagens, auditoria e imagem ficaram utilizáveis nas medidas observadas. As capturas de homologação ficam fora do repositório, no registro local desta execução. Não houve mudança no login, fotografia ou logo.

## Ambiente principal depois da atualização

| Verificação | Resultado |
| --- | --- |
| PostgreSQL original/volume | Preservados; saudável |
| Backend/frontend | Imagens homologadas 14.2.4; ativos e locais |
| Seed | false antes e depois do início |
| Flyway | V1–V10 válidas |
| Usuários | 5; 4 ativos |
| Administradores | 2 registros; somente Administrador Principal ativo |
| Login Principal / antigo | 200 / 401 |
| Chamados / ativos | 7 / 4 |
| Auditorias de chamados / contas | 21 / 1 |
| Comentários / anexos | 0 / 1 |
| Auditorias patrimoniais / alterações de campos | 0 / 0, sem testes no principal |
| Imagem histórica | HTTP 200, image/png, 185 bytes, nosniff |
| DPAPI e senhas | Preservados; credencial recuperável |

Comparação canônica das oito tabelas históricas incluiu campos sensíveis em memória, sem exposição, e bytes dos anexos. Colunas novas foram verificadas separadamente: quatro ativos e sete chamados com versão zero, snapshots antigos de resolução nulos e justificativas dos 21 eventos históricos nulas. A cópia atual de 26/11 também confirmou todas as 14 tabelas, sem exclusões de colunas.

Sistema disponível em http://localhost:4200. O override operacional com imagens locais permanece fora do Git; docker-compose.yml, credenciais e .env não foram substituídos.

## Arquivos desta entrega

- Backend: controllers/AssetController.java e TicketController.java; domain/Asset.java e domain/entities/Ticket.java; novos domain/entities/AssetAuditLog.java, dto/AssetAuditResponseDTO.java e repositories/AssetAuditLogRepository.java; DTOs AssetRequestDTO, AssetResponseDTO e AssetStatusUpdateDTO; repositories/AssetRepository.java e TicketAuditLogRepository.java; security/SecurityConfig.java; services/AssetService.java, TicketService.java e novo AttachmentImages.java; exceptions/GlobalExceptionHandler.java; application.properties; migração V10.
- Testes backend: novo AssetAttachmentPostgresIntegrationTest, AttachmentImagesTest e TestImages; ajustes em AssetControllerTest, AssetServiceTest, TicketServiceTest, AccountMigrationPostgresIntegrationTest, CompanyWorkflowPostgresIntegrationTest, HelpDeskApiIntegrationTest, TicketAttachmentIntegrationTest, TicketAuditIntegrationTest e alvo explícito da migração antiga em TicketSlaPostgresIntegrationTest.
- Frontend: core/models/ticket.model.ts, core/services/asset.service.ts, features/assets/asset-form/asset-form.ts/.html/.spec.ts e asset-list/asset-list.html.
- Documentação: este relatório, README, PLANO-14.2 e ROADMAP. Alterações anteriores de chamados, atribuição e SLA foram preservadas e separadas no checkpoint das entregas 14.2.1–14.2.3.

## Continuidade e limites

A 14.2.5 continua pendente: jornada empresarial integrada, volume representativo, rotinas de backup/restauração e contingência operacional. Também permanecem decisões comerciais sobre manutenção/descarte com chamados abertos, reversibilidade do descarte, retenção e eventual escopo de inventário por funcionário. Nenhuma dessas políticas foi introduzida implicitamente.

A contingência de uma instalação com apenas um ADMIN ativo permanece nos termos de CONTINUIDADE-ADMINISTRATIVA.md; esta entrega não cria conta adicional nem recuperação privilegiada. Não houve alteração de senha ou reativação da conta administrativa antiga. O login e a identidade visual aprovados permanecem intactos.

Acompanhamento da publicação e da CI é informado ao final da execução; este relatório não presume resultado remoto antes do push. Sem avanço para a Etapa 15 ou chatbot.
