# HelpDesk Pro 🛠️

Um sistema completo de gestão de chamados de TI e ativos corporativos. Desenvolvido para agilizar o suporte técnico através de fluxos automatizados, cálculos de SLA baseados em prioridade e trilhas de auditoria.

🔗 **[Acessar a Demonstração Online](#)** *(Link será adicionado em breve)*  
📄 **[Acessar Swagger API](#)** *(Link será adicionado em breve)*  
📖 **[Ver o Diário de Desenvolvimento (Roadmap completo)](docs/ROADMAP.md)**

---

## 📸 Interface do Sistema

*(Adicione aqui 3 ou 4 prints das telas principais: Dashboard, Listagem de Chamados, Tela de Detalhes com SLA e a Tela de Novo Chamado)*

---

## 🚀 Funcionalidades Principais

* **Abertura e Gestão de Chamados:** Fluxo completo desde a criação até a resolução.
* **Gestão de Ativos de TI:** Vinculação inteligente de equipamentos em uso aos incidentes.
* **Controle de SLA (Service Level Agreement):** Cálculo automático de prazos (4h, 24h, 72h) com alertas visuais de atraso.
* **Auditoria de Alterações:** Rastreabilidade automática de quem alterou status, técnico ou solução, com registro de valor antigo e novo.
* **Dashboard Interativo:** Indicadores de chamados por status, prioridade e técnico.
* **Perfis de Acesso (RBAC):** Permissões distintas para Solicitante, Técnico e Administrador.
* **Upload de Anexos:** Suporte a imagens para evidenciar problemas.

---

## 🛠️ Stack Tecnológica

**Backend:** Java 21 | Spring Boot 3 | Spring Security (JWT) | Hibernate/JPA  
**Frontend:** Angular 18 | RxJS | HTML/CSS Nativo  
**Banco de Dados:** PostgreSQL | H2 (Testes)  
**Infraestrutura:** Docker | Docker Compose  

---

## ⚙️ Destaques Técnicos

* **Segurança:** Proteção de rotas e visibilidade de componentes baseada no cargo (Role) do usuário logado, processada nativamente no Spring Security e com Guards no Angular.
* **Prevenção de Duplicidade:** Interceptor e arquitetura isolada para uploads de anexos (multipart/form-data).
* **Testes Automatizados:** Alta cobertura de testes unitários e de integração validando regras de negócios e endpoints isolados.
  * Backend: **159 testes** aprovados.
  * Frontend: **133 testes** aprovados.

---

## 🐳 Como Executar Localmente

### Usando Docker (Recomendado)

Certifique-se de ter o Docker e o Docker Compose instalados.

1. Clone o repositório:

```bash
git clone https://github.com/seu-usuario/helpdesk-pro.git
```

2. Crie o arquivo de variáveis de ambiente:

```bash
cp .env.example .env
# Preencha as variáveis no arquivo .env se necessário
```

3. Suba o banco de dados e a API:

```bash
docker-compose up -d --build
```

A API estará disponível em `http://localhost:8080`.

### Executando o Frontend (Angular)

1. Acesse a pasta do frontend:

```bash
cd frontend
```

2. Instale as dependências e inicie o servidor:

```bash
npm install
npm start
```

O frontend estará disponível em `http://localhost:4200`.

---

## 🔐 Credenciais para Teste Local

* **Administrador:** `admin@helpdesk.com` | Senha: `123`
* **Técnico:** `ana@helpdesk.com` | Senha: `123`
* **Solicitante:** `carlos@helpdesk.com` | Senha: `123`