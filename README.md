# HelpDesk Pro

Sistema full stack para gestão de chamados técnicos e ativos de TI.

O HelpDesk Pro está sendo desenvolvido como projeto de portfólio para demonstrar conhecimentos em backend com Java e Spring Boot, persistência com PostgreSQL e frontend com Angular.

## Tecnologias

**Backend**
- Java 21
- Spring Boot 4.1.1
- Spring Data JPA e Hibernate
- Spring Security e JWT
- Springdoc OpenAPI (Swagger)
- Gradle
- PostgreSQL
- JUnit e MockMvc

**Frontend**
- Angular
- TypeScript
- RxJS
- HttpClient

## Funcionalidades implementadas

**Etapa 1 — Estrutura inicial do backend**
- Projeto Spring Boot com Gradle e Java 21
- Integração com PostgreSQL
- Aplicação executada localmente na porta 8080

**Etapa 2 — Gestão de usuários**
- Cadastro e listagem de usuários
- Perfis ADMIN, TECHNICIAN e REQUESTER
- E-mail único
- Senhas protegidas e não expostas nas respostas JSON

**Etapa 3 — Gestão de ativos de TI**
- Cadastro, listagem e edição de ativos
- Alteração de status
- Código de ativo único
- Persistência validada no PostgreSQL

**Etapa 4 — Gestão de chamados**
- Criação, consulta e listagem de chamados
- Categorias INCIDENTE_SISTEMA, INCIDENTE_EQUIPAMENTO e DUVIDA
- Prioridade definida automaticamente por categoria
- Vínculo de ativo obrigatório para incidentes de equipamento
- Atribuição de técnico com validação de perfil
- Fluxo de status CRIADO → ABERTO → EM_ATENDIMENTO → RESOLVIDO → FECHADO
- Cancelamento permitido nos status CRIADO ou ABERTO

**Etapa 5 — Histórico e comentários**
- Comentários vinculados a chamados e usuários
- Data e hora de criação definidas pelo backend
- Histórico consultado em ordem cronológica
- Validações para comentário vazio, chamado inexistente e usuário inexistente

**Etapa 6 — Segurança e autenticação**
- Proteção de endpoints com Spring Security
- Autenticação com token JWT
- Controle de acesso baseado no perfil do usuário
- Senhas protegidas com BCrypt

**Etapa 7 — Testes e documentação da API**
- Fluxos principais validados por meio de Master Flow HTTP
- Documentação interativa com Swagger/OpenAPI
- 111 testes automatizados aprovados

**Etapa 8 — Frontend Angular e login**
- Estrutura inicial do projeto Angular e rotas
- Tela de login integrada ao endpoint de autenticação do backend
- Proxy de desenvolvimento para comunicação com a API local
- JWT armazenado no navegador após login
- Redirecionamento para `/home` após autenticação
- Build e testes do frontend executados com sucesso

## Próximas etapas

- Criar telas de listagem e detalhes de chamados
- Criar telas de cadastro e edição de ativos
- Implementar o dashboard com indicadores

## Status

O backend da API foi implementado e validado para o escopo atual. O frontend está em desenvolvimento; o login já está integrado, e a tela `/home` ainda é provisória.

## Como executar localmente

### Pré-requisitos

- Java 21
- Node.js e npm
- PostgreSQL

### Backend

1. Crie um banco PostgreSQL chamado `helpdesk`.
2. Configure a variável de ambiente `DB_PASSWORD` com a senha do PostgreSQL.
3. No terminal, acesse a pasta `backend`.
4. Execute:

   ```bash
   ./gradlew bootRun
   ```

O backend será iniciado na porta `8080`.

### Frontend

1. Em outro terminal, acesse a pasta `frontend`.
2. Instale as dependências:

   ```bash
   npm install
   ```

3. Inicie o servidor de desenvolvimento:

   ```bash
   npm start
   ```

O frontend será disponibilizado em `http://localhost:4200`. Durante o desenvolvimento, o proxy Angular encaminha as chamadas da API para o backend local.

### Testes

Na pasta `backend`, execute:

```bash
./gradlew test
```

Na pasta `frontend`, execute:

```bash
npx ng test --watch=false
```