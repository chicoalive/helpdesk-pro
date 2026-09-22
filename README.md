# HelpDesk Pro

Sistema full stack para gestão de chamados técnicos e ativos de TI.
Este projeto está sendo desenvolvido como portfólio para demonstrar conhecimentos em desenvolvimento backend com Java e Spring Boot, persistência de dados com PostgreSQL e desenvolvimento frontend com Angular.

## Tecnologias

**Backend (Concluído)**
* Java 21
* Spring Boot 3
* Spring Data JPA / Hibernate
* Spring Security & JWT (JSON Web Tokens)
* Springdoc OpenAPI (Swagger)
* Gradle
* PostgreSQL
* Testes Automatizados (JUnit / MockMvc)

**Frontend (Em andamento)**
* Angular e RxJS

## Funcionalidades implementadas

**Etapa 1 — Estrutura inicial do backend**
* Projeto Spring Boot criado com Gradle e Java 21
* Integração configurada com PostgreSQL
* Aplicação executando localmente na porta 8080

**Etapa 2 — Gestão de usuários**
* Cadastro e listagem de usuários
* Perfis de usuário com enumeração de papéis (ADMIN, TECHNICIAN, REQUESTER)
* E-mail único validado pelo banco de dados
* Senha não exposta nas respostas JSON

**Etapa 3 — Gestão de ativos de TI**
* CRUD básico de ativos
* Cadastro, listagem e edição de ativos
* Alteração de status do ativo
* Código de ativo único
* Persistência validada no PostgreSQL

**Etapa 4 — Gestão de chamados**
* Criação, consulta e listagem de chamados
* Categorias: INCIDENTE_SISTEMA, INCIDENTE_EQUIPAMENTO e DUVIDA
* Prioridade automática por categoria
* Vínculo de ativo obrigatório para incidentes de equipamento
* Atribuição de técnico e validação de papéis
* Fluxo de status: CRIADO -> ABERTO -> EM_ATENDIMENTO -> RESOLVIDO -> FECHADO
* Cancelamento permitido em CRIADO ou ABERTO

**Etapa 5 — Histórico e comentários**
* Comentários vinculados a chamados e usuários
* Data e hora de criação definidas pelo backend
* Consulta do histórico de comentários por chamado em ordem cronológica
* Validações para comentário vazio, chamado inexistente e usuário inexistente

**Etapa 6 — Segurança e Autenticação**
* Proteção de rotas com Spring Security
* Autenticação via token JWT
* Controle de acesso baseado no perfil do usuário logado (RBAC)
* Criptografia de senhas com BCrypt

**Etapa 7 — Master Flow e Documentação da API**
* Validação ponta a ponta de fluxos de negócio (Master Flow HTTP)
* Documentação interativa configurada com Swagger/OpenAPI
* Ampla cobertura com 111 testes automatizados (unitários e de integração) passando com 100% de sucesso

## Próximas etapas
* Setup do projeto Frontend com Angular
* Criação de telas de login e gestão de chamados/ativos
* Dashboard com indicadores

## Status
Projeto em desenvolvimento.
O **Backend está 100% concluído**, blindado e validado com testes automatizados.
Etapa atual: **Início do desenvolvimento Frontend (Angular).**

## Como executar localmente

1. Clone este repositório.
2. Configure um banco PostgreSQL chamado `helpdesk`.
3. Defina a variável de ambiente `DB_PASSWORD` com a senha do PostgreSQL.
4. Acesse a pasta `backend`.
5. Execute:
   ```bash
   ./gradlew bootRun