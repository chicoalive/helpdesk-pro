# Continuidade administrativa

Este procedimento pertence ao responsável autorizado pela instalação. Não existe recuperação administrativa anônima, senha padrão, senha mestra ou endpoint que dispense autenticação. `provision-admin` recusa instalações que já tenham qualquer conta ADMIN, inclusive inativa; não é ferramenta de recuperação.

## Recuperação disponível

1. Se outro ADMIN estiver ativo, confirme a identidade do titular por canal independente. O titular solicita a recuperação; o ADMIN emite a autorização na aplicação. O titular redefine a senha em até 15 minutos e valida novo login. As sessões anteriores são revogadas.
2. Se houver sessão administrativa ainda válida, o titular pode autorizar sua própria solicitação após confirmar sua identidade. Conclua a recuperação e valide o novo login antes de encerrar a operação.
3. Sem sessão ou outro ADMIN, utilize a credencial guardada pelo responsável no Windows ou em cofre corporativo autorizado. DPAPI requer o mesmo contexto Windows; copiar o XML para outro computador/usuário não garante recuperação. Não imprima a senha nem a coloque em comandos, relatórios ou chamados.

No Windows autorizado, o caminho real deve vir do inventário privado, fora do Git:

```powershell
$arquivoCredencial = Read-Host 'Caminho do arquivo protegido da instalação'
$credencial = Import-Clixml -LiteralPath $arquivoCredencial
Set-Clipboard -Value $credencial.GetNetworkCredential().Password
# Cole somente no login local; depois limpe a área de transferência.
Set-Clipboard -Value ''
$credencial = $null
```

A área de transferência é uma exposição temporária: desative histórico/sincronização e limpe também qualquer histórico já habilitado. Prefira preenchimento por cofre quando disponível. A senha DPAPI precisa ser atualizada após qualquer redefinição real:

```powershell
$novaCredencial = Get-Credential
$novaCredencial | Export-Clixml -LiteralPath $arquivoCredencial
$novaCredencial = $null
```

O diretório deve ter acesso limitado ao responsável e SYSTEM. Valide a leitura e um novo login sem mostrar a senha. Não sobrescreva o único meio de contingência antes de verificar a nova credencial. Registre somente responsável, data UTC e resultado da verificação.

## DPAPI indisponível e senha perdida

- Identifique o responsável legal/local e abra um incidente restrito, sem credenciais.
- Verifique se ainda há sessão válida ou outro ADMIN e aplique o fluxo acima.
- Se o XML estiver indisponível, restaure apenas a cópia protegida desse arquivo e do contexto Windows, por procedimento corporativo autorizado. Não restaure o banco principal nem volte um backup antigo da aplicação para contornar o login.
- Se existir uma cópia independente da senha em cofre corporativo, use-a para entrar localmente, valide a identidade e renove os meios de contingência.
- Se todos esses meios faltarem, preserve banco, backups e auditoria e acione o responsável técnico pelo procedimento local da Etapa 14.2.5, descrito ao final deste documento e no guia operacional. Não altere hashes por SQL, reative conta de desenvolvimento ou habilite demonstração.

Controle local e endereço localhost, sozinhos, não autorizam uma redefinição. A manutenção implementada exige identidade Windows, propriedade da configuração privada, chave DPAPI, controle Docker local, backup restaurado e verificado e operação exclusiva auditada. Não há autorização por uma simples confirmação digitada.

## Continuidade com um único ADMIN ativo

Com apenas um ADMIN ativo, a perda de senha, sessões e cofre exige manutenção local pelo responsável técnico autorizado. Mantenha e ensaie meios independentes: responsável substituto formalmente autorizado, cofre corporativo e recuperação do contexto Windows. Backup do PostgreSQL não substitui recuperação de credenciais. Nenhuma conta ou credencial real foi criada ou alterada nesta consolidação.

## Etapa 14.2.5 — Contingência local implementada

A limitação histórica acima foi resolvida por um procedimento explícito de manutenção técnica local, entregue e ensaiado somente em laboratório. Consulte [Guia operacional — contingência de ADMIN](GUIA-OPERACIONAL.md#contingência-local-de-admin--procedimento-excepcional).

O procedimento exige operador Windows autorizado, arquivos privados com ACL restrita, chave de assinatura protegida por DPAPI, controle do Docker Desktop local, identificação exata de um ADMIN ativo e backup restaurado e validado. Não depende do login perdido, não tem rota HTTP privilegiada, não reativa contas e não altera a finalidade de provision-admin. Localhost ou uma confirmação digitada não autorizam a operação.

O comando é recover-admin, acionado pelo wrapper operacional. A senha é gerada em memória e guardada em um novo arquivo DPAPI antes de ser aplicada. Transação e auditoria de manutenção, separada de um ator da aplicação, preservam a rastreabilidade. Sessões e autorizações anteriores são revogadas. Em falha, preserve a candidata DPAPI e investigue o resultado antes de tentar novamente.

A contingência não substitui cofre corporativo, segundo responsável autorizado e cópia protegida fora do computador. Controle administrativo do host/Docker faz parte da fronteira de confiança. A Etapa 14.2.5 não recuperou nem modificou o Administrador Principal real e não reativou a conta antiga.
