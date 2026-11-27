# Homologação 14.2.5 — Rotinas e homologação de empresa

Concluída localmente em 26/11/2026, a partir de main/85167b3. Etapas 14.2.1–14.2.4 preservadas. Etapa 15 e chatbot continuam suspensos. Resultado de publicação/CI informado no encerramento da execução; não presumido antes do envio.

## Implementação

1. **Rotinas Windows permanentes:** `ops/MeuAssistenteTI.ps1`, módulo de operações e testes. Initialize, Start, Health, Stop, MaintenanceStop, Diagnose, Backup, VerifyBackup, RestoreLab e RecoverAdmin. Não dependem de arquivos temporários da sessão do desenvolvedor. Configuração, override e meios protegidos ficam fora do Git.
2. **Manutenção local de ADMIN:** comando exclusivo recover-admin, sem rota HTTP, seed, migração automática ou senha padrão. Autorização Windows/SID/ACL e assinatura RSA com chave DPAPI; destino identificado por conta, banco, instalação e servidor. Backup restaurado e validado é obrigatório. Senha aleatória é salva em DPAPI antes da transação, sem sobrescrever arquivo existente. BCrypt, revogação de sessões/autorizações, bloqueios, versões, proteção de repetição e auditoria própria foram implementados.
3. **V11:** acrescenta somente maintenance_installation e maintenance_audit_logs, com índice e referências. V1–V10 foram preservadas. Operador de manutenção não é representado por um actor_id de usuário da aplicação. Não foram alteradas contas ou senhas pela migração.
4. **Jornada integrada ampliada:** CompanyWorkflowPostgresIntegrationTest passa a incluir informações solicitadas/respondidas, encerramento, cancelamento e recuperação normal com revogação de JWT e recusa de reutilização.
5. **CI:** acrescentada validação dos scripts e guardas de autorização em runner Windows. Mantidas regressões e builds existentes em PostgreSQL efêmero no job principal.

Não houve mudança no login, logo, fotografia, CSS aprovado, autenticação HTTP, SLA ou políticas de transição/atribuição/descarte. Nenhuma nova arquitetura de inventário foi introduzida.

## Segurança operacional

- Initialize verifica o proprietário Windows da configuração privada da instalação; a configuração autoriza esse SID. Arquivos privados exigem propriedade e ACL restritas ao operador/SYSTEM. A chave DPAPI assina a solicitação; o backend verifica a assinatura e o SID, não apenas uma string declarada pelo solicitante.
- Docker Desktop Linux local é fixado no comando, evitando um contexto remoto acidental. Projeto, contêiner, volume e portas são conferidos; PostgreSQL não publica porta. Seed=false é verificado antes do backend.
- Start reutiliza o contêiner/volume PostgreSQL existente. Stop não remove dados. Inicialização automática do Docker com Windows foi consultada e permaneceu false.
- Backup usa pg_dump custom, comparação de conteúdo, sequências, SHA-256 e manifesto sem segredos. Mudança de dados observada durante a cópia impede declarar o backup concluído. RestoreLab cria outro projeto e volume e identifica o destino antes de restaurar.
- Recuperação exige backend parado, backup e restauração prévia. Solicitação assinada vale por cinco minutos, tem request_id e versões esperadas e é recusada se o estado copiado mudou. Mutex Windows e bloqueio consultivo/transacional PostgreSQL protegem concorrência. Requisição antiga não reaplica uma senha.
- Alteração de senha, revogação e auditoria de sucesso são transacionais. Recusas autenticadas e diário privado registram resultado sem credenciais. Falha de auditoria de sucesso desfaz a alteração. Em falha de transporte/resultado incerto, a candidata DPAPI é preservada e deve ser usada para investigar; não se presume que todo erro ocorreu antes de commit.
- Não há instrução de edição de hashes, reativação de conta antiga, conta adicional automática ou recuperação anônima. provision-admin mantém sua finalidade original.

O modelo de confiança é explícito: controle autorizado do host, Docker e credenciais de banco equivale a administração técnica da instalação. A manutenção não protege contra o próprio administrador do host. A empresa deve controlar esse acesso; uma confirmação digitada/localhost não o substitui. Perda da chave/contexto Windows exige restauração ou recredenciamento técnico autorizado, sem sobrescrever os meios anteriores.

## Jornadas executadas

### PostgreSQL e HTTP reais, em laboratório

A cópia foi restaurada do principal e validada antes das novas gravações fictícias. Foram criados dois administradores fictícios, dois técnicos, dois solicitantes e um equipamento, sem tocar no principal.

- Perfis entraram com funções permitidas; solicitante sem gestão administrativa, técnico sem cadastro de usuários.
- Incidente de equipamento com PNG real, leitura por titular e equipe; chamado de sistema e solicitação de acesso no teste integrado.
- Técnico assumiu chamado; ADMIN atribuiu e transferiu com justificativa. Técnico desativado perdeu login/JWT e o atendimento continuou com outro técnico.
- Comentários, AGUARDANDO_USUARIO, resposta do titular, retomada, solução, RESOLVIDO e FECHADO foram executados. Cancelamento em condição permitida foi confirmado.
- Dashboard, detalhes, histórico/auditoria e conflito de ativo 409 foram conferidos.
- Recuperação normal concluiu, revogou a sessão anterior e recusou reutilizar a autorização.

### Recuperação emergencial, somente fictícia

Foram executadas duas recuperações em administradores fictícios, inclusive uma após acrescentar o diário operacional. A rotina fez backup/restauração, gerou credencial DPAPI exclusiva, alterou a conta escolhida e comprovou login e acesso administrativo. A conta real Administrador Principal e o ADMIN antigo não foram alvos.

Na primeira recuperação: nova senha 200, antiga 401, JWT antigo 401, autorização pendente REVOKED sem token_hash, um evento SUCCESS com operador Windows e demais usuários idênticos. A segunda confirmou diário privado SUCCESS, backend retomado e Health incluindo proxy/CORS.

Testes PostgreSQL cobrem sucesso, SID/chave sem autorização, assinatura inválida, destino errado, inexistente/não ADMIN/inativo, solicitação expirada, operação incorreta, backup ausente/adulterado/não restaurado/desatualizado, senha fraca, concorrência, repetição e falha real de auditoria com rollback. A migração V10–V11 preserva conta existente e não inventa evento de manutenção.

### Navegador

Desktop 1440×900 e celular 390×844: login do ADMIN recuperado, dashboard, filtro de chamados, gestão de usuários com três perfis, detalhe, solução e auditoria. Márcia Técnica assumiu chamado, iniciou atendimento, comentou e solicitou informações. José Solicitante respondeu pelo celular, consultou imagem 512×288 e recebeu recusa ao tentar o chamado de outro titular. Acentos e horários de Brasília apareceram corretamente.

O comprovante foi acionado pelo titular e o fluxo retornou após Escape, sem impressão física. A prévia gráfica de impressão não foi capturada pelo navegador integrado; não é declarado ensaio de impressora. O botão permanece destinado ao solicitante conforme a regra anterior, sem ampliar controles do ADMIN.

Capturas e evidências privadas permanecem fora do Git. Nenhuma nova identidade visual foi aplicada.

## Defeitos operacionais corrigidos

- A origem CORS do laboratório não correspondia à porta real do frontend, produzindo 403 no navegador apesar de login direto 200. RestoreLab agora define a origem específica antes da aplicação.
- Porta dinâmica do frontend mudava ao reiniciar e invalidava endereço/CORS. O laboratório agora recebe porta local fixa na criação. A saúde inclui acesso pelo proxy com Origin, não só HTTP direto.
- Nginx é retomado após backend para evitar referência antiga ao serviço durante manutenções/recriações.
- Comandos auxiliares dependiam de caminhos temporários e configuração implícita. Foram consolidados em rotinas versionadas com configuração privada estável, contexto local fixo e validação de recursos.
- A primeira regressão encontrou um teste histórico de V9–V10 que passou a executar também V11. O alvo foi fixado em V10 para preservar a finalidade do teste; a migração V11 tem teste próprio.

Não foram encontrados gargalos que justificassem alterar contratos, paginação ou arquitetura dentro do volume medido.

## Backup, restauração e indisponibilidade

Foram ensaiadas cópia do principal V10 para V11 e outra restauração do laboratório já V11 com massa ampliada. Projetos e volumes novos foram identificados por nome, rótulos e identidade Compose. Nenhuma restauração sobrescreveu o principal.

| Ensaio | Resultado observado |
| --- | --- |
| Backup inicial principal | 15,45 s; hash válido |
| Restauração inicial e aplicação V11 | 40,48 s; 14 tabelas originais preservadas e migrações anteriores idênticas |
| Backup com massa ampliada | 19,13 s |
| Restauração com massa ampliada | 29,05 s; 16 tabelas e nove sequências idênticas antes de novas gravações |
| Jornada na cópia recuperada | Logins, dashboard, equipamento ID 106, chamado ID 2012, comentário e anexo histórico válidos |
| Parada/indisponibilidade controlada e retomada | 15,40 s; indisponibilidade detectada, mesmos dados das 16 tabelas e sequências |
| Ensaio final pré-implantação | Novo backup principal e restauração isolada com V11 e Health aprovados |

Tempos de parede medidos no computador de desenvolvimento, com imagens já disponíveis e outros processos ativos; não são SLA/RTO/RPO contratados. A indisponibilidade foi graciosa/controlada, não queda abrupta ou perda de disco. Não houve ensaio em outro computador. É necessária cópia protegida fora deste computador e prática periódica de restauração; nenhum arquivo foi enviado para serviço externo.

## Volume medido

Geração sintética direta no laboratório, claramente separada da jornada pela API: 120 usuários, 100 ativos, 2.000 chamados, 4.000 comentários e 6.000 eventos de chamados adicionais. Esses eventos são fixtures de carga, não registros de ações humanas reais.

Totais medidos: **131 usuários, 105 ativos, 2.011 chamados, 4.004 comentários, 6.042 auditorias de chamados, 15 auditorias de contas e duas de ativos**; manutenção possui diário separado. Docker 29.9.3, PostgreSQL 15, Java 21, 12 CPUs e aproximadamente 15,5 GiB disponíveis ao engine. Windows/Docker Desktop, chamadas locais.

Uma chamada de aquecimento e 24 requisições por operação, com até oito simultâneas: **144 requisições medidas, zero erros**.

| Operação | Mediana | P95 | Máximo |
| --- | ---: | ---: | ---: |
| Listar chamados | 415,1 ms | 498,9 ms | 500,5 ms |
| Listar ativos | 40,9 ms | 107,7 ms | 155,2 ms |
| Dashboard | 126,8 ms | 206,8 ms | 222,8 ms |
| Detalhe | 22,6 ms | 112,7 ms | 115,5 ms |
| Auditoria do chamado | 20,8 ms | 48,9 ms | 53,8 ms |
| Comentários do chamado | 25,5 ms | 65,4 ms | 67,9 ms |

A lista transferiu aproximadamente 1,74 MB. No navegador, um ensaio único de recarga/renderização de 2.011 registros levou 2.123 ms e filtro por VOLUME-2000 levou 228 ms, restando uma linha. Esses tempos incluem custo da ferramenta de automação e não são medição isolada do JavaScript. Não extrapolar para mais usuários, empresas, WAN ou volumes maiores. Paginação e limites comerciais deverão ser reavaliados conforme crescimento.

## Regressão e builds

- **Backend: 516 aprovados, zero falhas, erros ou pulados**, PostgreSQL real isolado e demais testes. São os 502 anteriores mais 14 cenários de manutenção, além da ampliação da jornada existente.
- **Frontend: 250 aprovados em 24 arquivos**, sem mudanças na identidade ou telas.
- **Operação Windows: dez verificações aprovadas**, incluindo sintaxe, hash/formato de backup, distinção entre hash e restauração, SID e ACL.
- bootJar, imagem backend e build de produção Angular concluídos. Mantidos os dois avisos CSS conhecidos de chamados e usuários; orçamentos não foram aumentados. Jackson informa uso de API de leitura depreciada, sem erro de compilação.
- CI inclui PostgreSQL efêmero e novo job operacional Windows. Resultado e commit finais são informados após o push.

## Principal após aplicação

Backend 14.2.5 aplicado depois dos ensaios e backup validado; frontend aprovado 14.2.4 preservado. Mesmos contêiner e volume PostgreSQL. V1–V11 conferidas. Seed=false antes/depois, portas somente locais, Docker sem autostart Windows.

Preservados: **5 usuários, 4 ativos; dois registros ADMIN, um ativo; sete chamados; quatro equipamentos; 21 auditorias de chamados; uma auditoria administrativa; um anexo; zero comentários.** Auditorias patrimoniais seguem sem eventos fictícios. Nova auditoria de manutenção no principal permanece **zero**. A V11 adicionou somente identidade/metadados e estruturas vazias de auditoria.

Comparações canônicas cobriram campos e relacionamentos, inclusive senhas em memória sem exposição, bytes do anexo e sequências. Registros antigos de Flyway permanecem idênticos, com V11 adicionada. DPAPI principal inalterado e utilizável. Login do Principal e consultas administrativas 200; antigo ADMIN inativo, login 401. Anexo histórico PNG: 200, 185 bytes. Nenhuma recuperação emergencial ou reativação foi executada no principal.

Configuração operacional permanente: D:\Estudo\Configuracao\MeuAssistenteTI\installation.json. Sistema: http://localhost:4200. O override da sessão anterior não é mais necessário para as rotinas; foi preservado. Backups e credenciais anteriores não foram limpos nem sobrescritos.

## Arquivos alterados

- BackendApplication.java; novos bootstrap/MaintenanceSnapshot.java, MaintenanceAuthorization.java e LocalAdminRecovery.java; V11__local_maintenance_audit.sql.
- Testes: novo LocalMaintenancePostgresIntegrationTest.java; ampliação de CompanyWorkflowPostgresIntegrationTest.java; contagens de AccountMigrationPostgresIntegrationTest.java; alvo histórico em AssetAttachmentPostgresIntegrationTest.java.
- ops/MeuAssistenteTI.ps1, ops/MeuAssistenteTI.Operations.psm1 e ops/Tests/Operations.Tests.ps1.
- .github/workflows/ci.yml; README; GUIA-OPERACIONAL; CONTINUIDADE-ADMINISTRATIVA; PLANO-14.2; ROADMAP; este relatório.
- Configuração privada fora do Git: override com imagem nova, chave de manutenção DPAPI, configuração da instalação, backups e laboratórios. Credencial administrativa principal não foi alterada.

## Limites e decisões em aberto

Critérios operacionais da 14.2.5 atendidos dentro dos ensaios descritos. Não é certificação de desastre, capacidade ilimitada, disponibilidade contratada ou teste físico de impressora. Não foram implementadas exclusões automáticas nem promoção automática de restauração sobre o principal.

Permanecem decisões comerciais já registradas: reabertura, comentários/edição de solução em estados finais, calendário útil/pausas, descarte com chamado aberto, reversibilidade do descarte, inventário por funcionário e retenção. Nenhuma política foi alterada nesta entrega. Rotina corporativa de cópia externa, responsáveis substitutos e ensaio periódico deve ser mantida pela empresa. Etapa 15 e chatbot suspensos.
