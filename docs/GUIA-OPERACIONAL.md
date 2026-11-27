# Guia operacional — Meu Assistente TI

Rotinas entregues na Etapa 14.2.5. Windows, PowerShell **7.4 ou superior**, Docker Desktop com engine Linux local e imagens já construídas. Os comandos não publicam o sistema na rede, não limpam volumes e não configuram inicialização com o Windows.

## Responsabilidades e autorização

| Responsável | Operações |
| --- | --- |
| Administrador da aplicação | Usuários, chamados, equipamentos, auditorias e recuperação normal após verificar a identidade |
| Responsável técnico da instalação | Docker, configuração privada, backups, restauração isolada, manutenção local e guarda dos meios de contingência |

A manutenção exige o proprietário Windows da configuração da instalação, seu SID autorizado, acesso ao Docker Desktop local e a chave de assinatura protegida por DPAPI. Não basta uma confirmação digitada ou uma requisição para localhost. A inicialização da configuração verifica o proprietário de `.env`; arquivos privados devem pertencer ao operador e permitir acesso somente a ele e SYSTEM. Execute com o usuário Windows responsável, sem trocar de contexto por conveniência.

**Limite de confiança:** controle do Docker e das credenciais de banco equivale a controle administrativo do banco. O procedimento protege contra chamadas HTTP, operadores Windows diferentes e solicitações sem assinatura; não promete proteger o banco contra o administrador do próprio host/Docker. A empresa deve delegar esse acesso técnico formalmente. A aplicação diferencia o operador Windows de uma conta ADMIN.

## Configuração permanente

Código versionado: `ops/MeuAssistenteTI.ps1` e seu módulo. Configuração particular, override, chave DPAPI, comprovantes e dumps ficam **fora do repositório**. Não use pastas temporárias do Codex nem coloque segredos em parâmetros, transcrições, depuração ou logs.

Nesta instalação, a configuração permanente está em:

`D:\Estudo\Configuracao\MeuAssistenteTI\installation.json`

Ela reaproveita o projeto `helpdesk-pro`, o volume existente, `.env` e o override operacional. As credenciais administrativas existentes não foram movidas. O contexto Docker é fixado em `desktop-linux`, impedindo uso acidental de um contexto remoto.

Para uma instalação autorizada que ainda não tenha configuração operacional, o técnico executa uma vez, informando seu override já revisado:

```powershell
Set-Location D:\Estudo\Projetos\helpdesk-pro
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action Initialize `
  -Config D:\Estudo\Configuracao\MeuAssistenteTI\installation.json `
  -Repository D:\Estudo\Projetos\helpdesk-pro `
  -OperationalOverride C:\caminho\privado\override-operacional.yml `
  -BackupRoot D:\Estudo\Backups\helpdesk-pro
```

Repetir Initialize valida a configuração existente, sem sobrescrevê-la. Uma inicialização parcial preserva a chave existente e recusa inconsistências. O override deve declarar imagens locais compatíveis, backend/frontend em 127.0.0.1, PostgreSQL sem publicação e seed explicitamente false. Initialize não constrói imagens, não cria usuários e não altera a senha de ninguém.

## Iniciar, consultar e parar

```powershell
Set-Location D:\Estudo\Projetos\helpdesk-pro
$config = 'D:\Estudo\Configuracao\MeuAssistenteTI\installation.json'
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action Start -Config $config
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action Health -Config $config
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action Diagnose -Config $config
```

Start é deliberado: abre Docker Desktop se necessário, sem habilitar autostart do Windows. Reutiliza o contêiner PostgreSQL quando ele existe, aguarda sua saúde e inicia a aplicação sem rebuild/pull. Confere seed e isolamento antes de iniciar backend. Atualiza o Nginx após a retomada para evitar resolução antiga do backend. Configuração incompatível, portas expostas, projeto/volume ambíguos ou falha de prontidão interrompem a operação.

Health verifica contêineres, volume, PostgreSQL, API protegida (401 sem token), frontend e proxy/CORS da origem local. Não equivale a um teste autenticado de todas as funções. Diagnose mostra imagens, estados, volumes, contagens/migrações e os últimos vinte resultados de manutenção, com operador e conta, sem despejar variáveis, logs gerais ou senhas.

```powershell
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action Stop -Config $config
```

Stop para os serviços preservando contêineres e volumes. Não executa down, remoção ou limpeza. Os comandos retornam **0 no sucesso e 1 em falha**, com mensagens em português. Não continue uma atualização depois de uma falha de saúde.

Acesso normal: http://localhost:4200. Backend: 127.0.0.1:8080. PostgreSQL sem porta publicada. Docker permanece com inicialização automática desabilitada nesta instalação.

## Backup, verificação e restauração isolada

```powershell
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action Backup -Config $config
```

O resultado informa BackupDirectory. A rotina cria subpasta exclusiva no destino protegido, dump custom do PostgreSQL e `manifest.json`. O manifesto registra SHA-256 do dump, dados canônicos de tabelas/sequências, migrações, projeto, identidade do servidor, imagens e seus IDs, commit e necessidade de configuração/JWT externos. Não contém senhas ou JWTs. Inclui bytes dos anexos, usuários, chamados, ativos, comentários e auditorias.

pg_dump usa snapshot consistente. A rotina compara os dados antes/depois e recusa declarar concluída uma cópia se houve gravação concorrente observada. Em operação movimentada, escolha uma janela sem gravações ou pare somente o backend de forma planejada. Um backup só por hash **ainda não é uma restauração homologada**.

```powershell
$backup = Read-Host 'BackupDirectory informado pela rotina'
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action VerifyBackup -Config $config -BackupDirectory $backup
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action RestoreLab -Config $config -BackupDirectory $backup
```

RestoreLab **sempre cria outro projeto, banco e volume identificados**; não recebe um destino arbitrário e nunca sobrescreve o principal. Confere rótulos do projeto/volume antes do pg_restore, compara todas as tabelas e sequências e inicia a aplicação. Migrações anteriores devem continuar idênticas; novas migrações podem acrescentar estruturas sem alterar dados históricos. Após o ensaio, o manifesto passa a indicar RestoreVerified, duração e caminho da configuração do laboratório.

Uma atualização de esquema pode ser ensaiada explicitamente com uma imagem nova já construída:

```powershell
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action RestoreLab -Config $config `
  -BackupDirectory $backup -RestoreBackendImage helpdesk-pro-14-2-5:local
```

A cópia usa senha de banco e JWT aleatórios próprios, mantidos por DPAPI. O frontend recebe uma porta local fixa escolhida para o laboratório, e somente essa origem é incluída no CORS. Consulte seu endereço em Diagnose/na configuração Compose privada. Utilize a configuração retornada para Health, Stop e Start do laboratório. Não a confunda com a configuração principal.

A rotina verifica estrutura, conteúdo e sequências; o responsável ainda deve validar login, anexo e uma pequena jornada autenticada, como realizado nesta homologação. Ela não promove a cópia a principal automaticamente. Uma retomada real deve escolher a cópia homologada e sua aplicação compatível; preservar o volume original e planejar a troca dos endereços com o responsável técnico.

Não há exclusão automática de backups, volumes ou laboratórios. Ao terminar um ensaio, use Stop com a configuração do laboratório. A decisão de descarte é separada e deve identificar positivamente os recursos.

### Guarda fora do computador e compatibilidade

O dump não substitui a configuração JWT, os arquivos protegidos e as imagens compatíveis. Guarde cópia protegida independente do computador, com controle de acesso e criptografia corporativa adequada, e ensaie a restauração periodicamente. Nenhum arquivo foi enviado a serviço externo nesta entrega. Copiar DPAPI para outro usuário/computador não garante descriptografia; planeje o cofre/contexto Windows e responsáveis substitutos.

**V11 não é desfeita por trocar a imagem.** Não faça downgrade cego: Flyway pode recusar imagem sem a migração aplicada. Para recuperação de versão, restaure a cópia compatível em novo volume com sua aplicação correspondente, valide e só depois planeje a troca. Nunca use down -v, recriação do banco ou edição de hashes como procedimento de recuperação.

## Recuperação normal de senha

Continue usando a aplicação: solicitação genérica, verificação de identidade pelo ADMIN, autorização exibida uma única vez, entrega por canal confiável e redefinição pelo titular. Sem serviço de e-mail. Sessões antigas são revogadas; autorizações expiram e não são reutilizáveis. Atualize os meios de guarda somente após validar a nova credencial.

## Contingência local de ADMIN — procedimento excepcional

**Não executar como teste no principal.** Os ensaios desta entrega ocorreram somente com administradores fictícios em laboratório.

1. Abra incidente restrito, confirme autorização institucional e identifique o operador técnico. Verifique primeiro outro ADMIN, sessão válida e cofre/DPAPI; não use manutenção por conveniência.
2. Confirme instalação e conta por **ID e e-mail exato**. A conta deve existir, ser ADMIN e estar ativa. Conta inativa não é reativada; provision-admin permanece exclusivo para instalação sem qualquer ADMIN.
3. Planeje indisponibilidade e pare somente o backend dessa instalação:

```powershell
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action MaintenanceStop -Config $config
```

4. Execute com a identidade Windows autorizada. O exemplo pede somente identificadores/motivo, nunca a senha:

```powershell
$id = [long](Read-Host 'ID confirmado do ADMIN ativo')
$email = Read-Host 'E-mail exato confirmado'
$motivo = Read-Host 'Referência do incidente e motivo, sem segredos'
$novaCredencial = 'D:\Estudo\Credenciais\MeuAssistenteTI\recuperada-' + (Get-Date -Format yyyyMMdd-HHmmss) + '.credential.xml'
pwsh -File .\ops\MeuAssistenteTI.ps1 -Action RecoverAdmin -Config $config `
  -UserId $id -Email $email -Reason $motivo -NewCredentialFile $novaCredencial
```

A rotina exige backend parado, gera backup e o restaura em outro volume antes de tocar na senha. Gera senha criptograficamente aleatória, grava e relê um **novo** arquivo DPAPI antes da alteração; arquivo existente não é sobrescrito. Assina uma solicitação breve com a chave DPAPI do operador. Senha e assinatura são transmitidas por entrada padrão, nunca por argumentos, arquivos de texto ou variáveis de ambiente.

O comando backend `recover-admin` fica sem porta HTTP, seed e migração automática, com logs gerais desligados. Confere assinatura/SID, prazo, banco, ID de instalação, servidor e bytes do backup montado somente para leitura. Dentro da transação, bloqueia a manutenção, relê a conta, verifica versões e o estado que foi copiado, aplica BCrypt, revoga JWTs e autorizações pendentes e grava auditoria. Não altera nome, e-mail, perfil, estado ou demais usuários. Requisição repetida/desatualizada é recusada.

Mutex no Windows reduz execuções duplicadas; bloqueio consultivo PostgreSQL, bloqueios de contas, versões e request_id único protegem também a concorrência entre processos. Falha na auditoria de sucesso desfaz a alteração. Recusas autenticadas têm registro de falha; se o banco não conseguir registrar, o diário privado do operador continua sendo o ponto de investigação.

A auditoria `maintenance_audit_logs` identifica o operador Windows, conta, motivo, instante, backup e resultado, separada de `user_audit_logs`. Não há actor_id fictício de usuário. O diário `maintenance-journal.jsonl` fica na pasta privada, sem senhas/tokens. Operações sem assinatura válida não recebem identidade confiável no banco.

5. No sucesso, o script retoma backend/frontend, valida novo login e acesso administrativo, sem exibir senha/token. Preserve o arquivo antigo até concluir a verificação. A nova senha fica no arquivo DPAPI informado; a recuperação não sobrescreve o arquivo principal automaticamente.
6. Para entrar manualmente, use cofre autorizado ou o procedimento de uso temporário da credencial descrito em CONTINUIDADE-ADMINISTRATIVA.md. Não imprima a senha. Se utilizar clipboard, mantenha histórico/sincronização desabilitados e limpe imediatamente após o login.
7. Em falha ou resultado incerto, **preserve a nova credencial candidata e os logs**. Não repita às cegas: verifique auditoria e acesso usando a candidata. FAILED/REFUSED não autoriza supor que uma indisponibilidade de conexão nunca ocorreu após commit. Corrija a causa, gere novo backup e use outro nome de arquivo se uma nova tentativa for necessária. Somente a confirmação autenticada encerra a manutenção.

### Perda do contexto Windows ou da chave de manutenção

A chave é independente da senha do ADMIN, mas DPAPI ainda depende do contexto Windows. Se ela também estiver indisponível, o responsável autorizado do host deve restaurar seus meios protegidos. Recredenciamento técnico exige domínio real sobre a instalação, propriedade autorizada de sua configuração privada e acesso Docker; Initialize pode preparar **outro diretório privado**, sem sobrescrever a configuração anterior. Transferência de propriedade/acesso do sistema operacional exige procedimento institucional do responsável do host. Isso não é recuperação HTTP nem uma senha mestra da aplicação.

## Limites desta homologação

Foram ensaiados PostgreSQL 15, reinício gracioso e indisponibilidade controlada local. Não foram simulados queda abrupta, disco perdido, corrupção física ou migração para outro computador. O ensaio de volume não equivale a capacidade garantida: 2.011 chamados totais, oito requisições simultâneas e os tempos registrados no relatório. Listagem continua sem paginação e deve ser reavaliada para volumes maiores. Regras comerciais de estados, SLA e descarte permanecem as aprovadas anteriormente.
