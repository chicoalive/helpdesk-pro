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
- Angular e RxJS (planejado para etapas futuras)

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

## Próximas etapas

- Modelagem de chamados técnicos
- Histórico e comentários dos chamados
- Autenticação e autorização com Spring Security e JWT
- Documentação da API com Swagger/OpenAPI
- Frontend Angular
- Dashboard com indicadores

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

## Status

Projeto em desenvolvimento. A primeira publicação reúne as Etapas 1, 2 e 3, que foram implementadas e testadas localmente antes da criação deste repositório Git.
