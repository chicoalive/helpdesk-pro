# Histórico de Desenvolvimento (Roadmap) — HelpDesk Pro

Registro técnico da evolução incremental do projeto **HelpDesk Pro**, organizado por etapas de implementação e validação.

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
  - `CRIADO → ABERTO → EM_ATENDIMENTO → RESOLVIDO → FECHADO`
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

## Etapa 12 — Unificação Docker Full Stack, Seed de Demonstração, CI e Documentação (EM ANDAMENTO)
- *Status atual: em andamento (documentação e screenshots validados localmente; aguardando revisão final, commit e push).*
- **Docker Compose Full Stack:** adição do `frontend/Dockerfile` multi-stage (`node:24-alpine` + `nginx:alpine`) e `frontend/nginx.conf` (roteamento SPA + proxy reverso `/api/` para `http://backend:8080/`).
- **Seed idempotente de demonstração (`DemoDataInitializer`):** ativado explicitamente via `APP_SEED_DEMO_ENABLED=true` no Docker Compose, populando usuários, ativos (`IN_USE` e `UNDER_MAINTENANCE`), chamados em diferentes estados/prioridades, histórico de auditoria e anexo PNG de exemplo sem duplicar registros em reinicializações.
- **Integração Contínua (CI):** workflow `.github/workflows/ci.yml` executando os **176 testes do backend** (Java 21 + Gradle) e **145 testes + build de produção do frontend** (Node 24 + Angular 22), totalizando **321 testes automatizados**.
- **Documentação e Vitrine Técnica:** reestruturação do `README.md` com arquitetura full stack, máquina de estados Mermaid real, credenciais de demonstração e screenshots reais em `docs/assets/`, separando o histórico evolutivo em `docs/ROADMAP.md`.
