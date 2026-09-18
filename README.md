# HelpDesk Pro

Sistema full stack para gestão de chamados técnicos e ativos de TI.

Este projeto está sendo desenvolvido como portfólio para demonstrar conhecimentos em desenvolvimento backend com Java e Spring Boot, persistência de dados com PostgreSQL e, nas próximas etapas, desenvolvimento frontend com Angular.

## Tecnologias

### Backend

- Java 21
- Spring Boot
- Spring Data JPA
- Hibernate
- Gradle
- PostgreSQL

### Frontend

- Angular e RxJS — planejado para etapas futuras

## Funcionalidades implementadas

### Etapa 1 — Estrutura inicial do backend

- Projeto Spring Boot criado com Gradle e Java 21
- Integração configurada com PostgreSQL
- Aplicação executando localmente na porta 8080

### Etapa 2 — Gestão de usuários

- Cadastro e listagem de usuários
- Perfis de usuário com enumeração de papéis
- E-mail único validado pelo banco de dados
- Senha não exposta nas respostas JSON

### Etapa 3 — Gestão de ativos de TI

- CRUD básico de ativos
- Cadastro, listagem e edição de ativos
- Alteração de status do ativo
- Código de ativo único
- Persistência validada no PostgreSQL

### Etapa 4 — Gestão de chamados

- Criação, consulta e listagem de chamados
- Categorias: `INCIDENTE_SISTEMA`, `INCIDENTE_EQUIPAMENTO` e `DUVIDA`
- Prioridade automática por categoria
- Vínculo de ativo obrigatório para incidentes de equipamento
- Atribuição de técnico e validação de papéis
- Fluxo de status: `CRIADO -> ABERTO -> EM_ATENDIMENTO -> RESOLVIDO -> FECHADO`
- Cancelamento permitido em `CRIADO` ou `ABERTO`
- Testes unitários e de integração

### Etapa 5 — Histórico e comentários

- Comentários vinculados a chamados e usuários
- Data e hora de criação definidas pelo backend
- Criação de comentário em um chamado
- Consulta do histórico de comentários por chamado
- Histórico retornado em ordem cronológica
- Validações para comentário vazio, chamado inexistente e usuário inexistente
- Testes unitários e de integração

## Próximas etapas

- Autenticação e autorização com Spring Security e JWT
- Documentação da API com Swagger/OpenAPI
- Frontend Angular
- Dashboard com indicadores

## Status

Projeto em desenvolvimento.

As Etapas 1 a 5 estão implementadas e validadas com testes automatizados isolados.

**Etapa atual: 6.**

## Como executar localmente

1. Clone este repositório.
2. Configure um banco PostgreSQL chamado `helpdesk`.
3. Defina a variável de ambiente `DB_PASSWORD` com a senha do PostgreSQL.
4. Acesse a pasta `backend`.
5. Execute:

```bash
./gradlew bootRun
```

No Windows, use:

```bash
gradlew.bat bootRun
```

A API ficará disponível em `http://localhost:8080`.