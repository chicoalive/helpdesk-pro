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
- Se todos esses meios faltarem, **pare: a versão atual não oferece recuperação segura implementada**. Preserve banco, backups e auditoria e solicite um procedimento de manutenção local formal, revisado e testado em cópia isolada. Não altere hashes por SQL, reative conta de desenvolvimento ou habilite demonstração.

Controle local do computador, sozinho, não corresponde a uma autorização de redefinição na aplicação. Nenhum comando de emergência foi implementado nesta consolidação. Uma futura solução exigirá identidade do operador verificada fora da aplicação, acesso local autorizado, backup validado, execução exclusiva, confirmação explícita da conta, entrada protegida da nova senha, auditoria, revogação de sessões e validação antes de liberar o serviço; isso é requisito pendente, não instrução executável hoje.

## Pendência comercial importante

Com apenas um ADMIN ativo, perder simultaneamente senha, sessões e meios externos de recuperação causa bloqueio administrativo. Antes do uso comercial, definir e ensaiar contingência independente: segundo responsável real com conta exclusiva ou cofre corporativo com acesso de emergência controlado e testado. Backup do PostgreSQL não substitui recuperação de credenciais. Nenhuma conta ou credencial real foi criada ou alterada nesta consolidação.
