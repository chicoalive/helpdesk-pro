# Histórico de Desenvolvimento (Roadmap) — HelpDesk Pro

Registro técnico da evolução incremental do projeto **HelpDesk Pro**, organizado por etapas de implementação e validação.

## Estado atual — Etapa 14

As Etapas **14.1A (instalação segura)**, **14.1B (gestão de contas e sessões)** e **14.1C (recuperação e redefinição de senhas)** estão implementadas e homologadas. Identidade pública: **Meu Assistente TI**. Suítes completas: **238 backend + 214 frontend**, sem falhas ou testes pulados na validação com PostgreSQL isolado.

O bootstrap administrativo padrão descrito no histórico abaixo foi substituído por provisionamento local seguro; demonstração exige ativação explícita. Flyway V1–V6, auditoria transacional e revogação de sessões compõem a base atual.

- [Homologação e pendências comerciais](HOMOLOGACAO-14.1.md).
- [Continuidade administrativa e limites da contingência](CONTINUIDADE-ADMINISTRATIVA.md).
- [Levantamento da Etapa 14.2](PLANO-14.2.md), ainda sem implementação.

A Etapa 15, incluindo publicação externa e empregabilidade, permanece suspensa. Os registros a seguir descrevem a evolução histórica e podem conter comportamentos posteriormente substituídos.

---

## Etapa 1 — Estrutura inicial do backend
- Projeto Spring Boot 4.1.1 estruturado com Gradle e Java 21.
- Configuração de persistência e integração com PostgreSQL.
- Aplicação configurada para execução na porta `8080`.

## Etapa 2 — Gestão de usuários
- Cadastro, consulta por ID e listagem de usuários (`POST /users`, `GET /users`, `GET /users/{id}`).
- Perfis de acesso definidos em `UserRole`: `ADMIN`, `TECHNICIAN` e `REQUESTER`.
- Validação de unicidade de e-mail.
- Proteção de credenciais (senhas não expostas nas respostas JSON via `WRITE_ONLY`).

## Etapa 3 — Gestão de ativos de TI
- Cadastro, listagem, consulta e atualização de ativos (`POST /assets`, `GET /assets`, `GET /assets/{id}`, `PUT /assets/{id}`, `PATCH /assets/{id}/status`).
- Estados de ativo definidos em `AssetStatus`: `INACTIVE`, `IN_USE`, `UNDER_MAINTENANCE` e `DISCARDED`.
- Validação de unicidade de código patrimonial (`code`).

## Etapa 4 — Gestão de chamados
- Criação, consulta e listagem de chamados (`POST /tickets`, `GET /tickets`, `GET /tickets/{id}`, `GET /tickets/requester/{requesterId}`).
- Categorias de chamado (`TicketCategory`): `INCIDENTE_SISTEMA`, `INCIDENTE_EQUIPAMENTO`, `SOLICITACAO_ACESSO`, `DUVIDA` e `OUTRO`.
- Cálculo automático de prioridade (`TicketPriority`) no backend conforme a categoria:
  - `INCIDENTE_SISTEMA` → `ALTA`
  - `INCIDENTE_EQUIPAMENTO`, `SOLICITACAO_ACESSO`, `OUTRO` → `MEDIA`
  - `DUVIDA` → `BAIXA`
- Vínculo obrigatório de ativo com status `IN_USE` para a categoria `INCIDENTE_EQUIPAMENTO`.
- Atribuição de técnico (`PATCH /tickets/{id}/technician`) com validação de perfil `TECHNICIAN` e transição automática `CRIADO → ABERTO`.
- Máquina de estados (`PATCH /tickets/{id}/status`):
  - `CRIADO → ABERTO → EM_ATENDIMENTO ↔ AGUARDANDO_USUARIO → RESOLVIDO → FECHADO`
  - Cancelamento (`CANCELADO`) permitido apenas a partir de `CRIADO` ou `ABERTO`.
  - Campo `solution` obrigatório para transição a `RESOLVIDO` e `FECHADO`.

## Etapa 5 — Histórico e comentários
- Registro e listagem de comentários em chamados (`POST /tickets/{id}/comments`, `GET /tickets/{id}/comments`).
- Data e hora de criação gerenciadas pelo backend e retorno em ordem cronológica.
- Validações de conteúdo obrigatório, existência do chamado e permissões do autor.

## Etapa 6 — Segurança e autenticação
- Proteção de endpoints com Spring Security (arquitetura `STATELESS`) e autenticação via token JWT (`POST /auth/login`).
- Senhas criptografadas com `BCryptPasswordEncoder`.
- Controle de acesso baseado em perfil (RBAC) nos endpoints de usuários, ativos, chamados, auditoria e dashboard.
- Bootstrap inicial (`DataInitializer`) garantindo a criação idempotente do administrador padrão.

## Etapa 7 — Testes de integração e documentação da API
- Padronização de respostas de erro HTTP (`400`, `401`, `403`, `404`, `409`, `422`) via `GlobalExceptionHandler`.
- Validação de fluxos ponta a ponta (Master Flow) com MockMvc e JUnit 5.
- Documentação interativa da API com Springdoc OpenAPI / Swagger UI (`/swagger-ui/index.html`).

## Etapa 8 — Frontend Angular e autenticação
- Projeto Angular 22 configurado com roteamento standalone, `HttpClient` e proxy de desenvolvimento (`/api`).
- Tela de login integrada ao endpoint `POST /auth/login`, armazenamento do JWT e interceptor HTTP para envio automático do header `Authorization: Bearer`.
- Redirecionamento autenticado para `/home`.

## Etapa 9 — Telas de chamados, ativos e controle por perfil
- Listagem de chamados com busca textual e filtros por status e prioridade.
- Tela de detalhes do chamado com exibição de metadados e histórico de comentários.
- Módulo de ativos (`/assets`, `/assets/new`, `/assets/:id/edit`) com formulários reativos e restrição por perfil (`ADMIN` gerencia, `TECHNICIAN` consulta, `REQUESTER` sem acesso ao inventário).
- Route guards (`authGuard`, `assetGuard`, `ticketGuard`) e cabeçalho dinâmico conforme o papel do usuário autenticado.

## Etapa 10 — Dashboard e indicadores operacionais
- Endpoint `GET /dashboard/summary` no backend agregando chamados por status, por prioridade, por técnico, chamados sem técnico atribuído e ativos em manutenção.
- Componente `DashboardSummary` integrado ao topo de `/home` para `ADMIN` e `TECHNICIAN` (oculto e sem requisição para `REQUESTER`).

## Etapa 11 — SLA, auditoria, anexos, prevenção de duplicidade e fluxo completo de chamados
- **SLA automático:** cálculo derivado de `slaDeadline` (`4h` para `ALTA`, `24h` para `MEDIA`, `72h` para `BAIXA`) e flag `slaBreached` para chamados ativos fora do prazo.
- **Auditoria de alterações (`GET /tickets/{id}/audit`):** registro automático de alterações de `technician`, `status` e `solution` com usuário autor, data/hora, valor anterior e novo valor, visível para `ADMIN` e `TECHNICIAN`.
- **Abertura de chamado pelo `REQUESTER` (`/tickets/new`):** endpoint `GET /assets/available` (retornando apenas ativos `IN_USE`), formulário simplificado sem jargões técnicos para o usuário comum e comprovante de chamado imprimível (`window.print()`).
- **Anexo de imagem (`POST /tickets/{id}/attachment`, `GET /tickets/{id}/attachment`):** suporte a 1 imagem PNG ou JPG/JPEG de até 2 MB por chamado.
- **Prevenção de chamados duplicados:** bloqueio de múltiplos chamados ativos do mesmo `REQUESTER` para `INCIDENTE_SISTEMA`, `SOLICITACAO_ACESSO` e para o mesmo equipamento em `INCIDENTE_EQUIPAMENTO`, com atalho direto na interface para acompanhar o chamado existente.
- **Atendimento pela interface (`/tickets/:id`):** controles operacionais para `ADMIN` e `TECHNICIAN` atribuírem técnico, alterarem status e registrarem solução técnica com atualização imediata da trilha de auditoria.

## Etapa 12 — Unificação Docker Full Stack, Seed de Demonstração, CI e Documentação
- **Docker Compose Full Stack:** adição do `frontend/Dockerfile` multi-stage (`node:24-alpine` + `nginx:alpine`) e `frontend/nginx.conf` (roteamento SPA + proxy reverso `/api/` para `http://backend:8080/`).
- **Seed idempotente de demonstração (`DemoDataInitializer`):** ativado explicitamente via `APP_SEED_DEMO_ENABLED=true` no Docker Compose, populando usuários, ativos (`IN_USE` e `UNDER_MAINTENANCE`), chamados em diferentes estados/prioridades, histórico de auditoria e anexo PNG de exemplo sem duplicar registros em reinicializações.
- **Integração Contínua (CI):** workflow `.github/workflows/ci.yml` executando os testes do backend e frontend + build de produção.
- **Documentação e Vitrine Técnica:** reestruturação do `README.md` com arquitetura full stack, máquina de estados Mermaid real, credenciais de demonstração e screenshots reais em `docs/assets/`.

## Etapa 12-R — Revisão Funcional Ponta a Ponta, Segurança e Preparação Pós-MVP (R0–R6)
- **R1 — Segurança e cadastro de usuários:** validação com Bean Validation (`@Valid`, `@NotBlank`, `@Email`, `@NotNull`) em `POST /users`, normalização de e-mail, tratamento de duplicidade com `400 Bad Request` e handler de integridade (`409 Conflict`), e codificação BCrypt incondicional (removendo bypass por prefixo `"$2"`).
- **R2 — Fluxo completo dos chamados:** formulário de envio de comentários em `/tickets/:id`, botão de 1 clique **"Assumir chamado"** para `TECHNICIAN` (sem depender de `GET /users` nem digitar ID numérico), botão **"Cancelar chamado"** para `REQUESTER` em chamados `CRIADO` ou `ABERTO`, suporte completo ao status `AGUARDANDO_USUARIO` (`EM_ATENDIMENTO ↔ AGUARDANDO_USUARIO → RESOLVIDO`) e filtro dinâmico de transições válidas no seletor de status.
- **R3 — Administração de usuários e autenticação:** tela administrativa `/users` (`UserManagement`) para listagem, filtro e cadastro de solicitantes e técnicos, visível apenas para `ADMIN`, proteção de rotas com `authGuard` e `userAdminGuard` e verificação de expiração (`exp`) do token JWT no cliente.
- **R4 — Usabilidade e desempenho:** consultas leves em `TicketRepository` (`findAll` e `findByRequesterId`) que não carregam os bytes (`byte[] attachmentData`) de anexos durante listagens, inclusão do claim `name` no JWT para exibição do nome do usuário no cabeçalho e colunas de **Solicitante** e **Técnico** na tabela principal de chamados.
- **R5 — Preparação segura para demonstração online:** remoção de fallback inseguro de segredo JWT no código-fonte e criação de `application-prod.properties` exigindo `JWT_SECRET`, configuração de CORS restrito a origens permitidas (`CORS_ALLOWED_ORIGINS`) em `SecurityConfig` e restauração automática de integridade das contas de demonstração em `DemoDataInitializer`.
- **R6 — Homologação e documentação:** validação completa com **351 testes automatizados** (**185 no backend** + **166 no frontend**) e atualização de `README.md` e `docs/ROADMAP.md`.
