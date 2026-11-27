#Requires -Version 7.4
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Assert-MatiOutsideRepository([string]$Path,[string]$Repository){
 if(![IO.Path]::IsPathFullyQualified($Path)){throw 'Use caminhos privados absolutos.'}
 $full=[IO.Path]::GetFullPath($Path).TrimEnd('\','/');$repo=[IO.Path]::GetFullPath($Repository).TrimEnd('\','/');if($full.Equals($repo,[StringComparison]::OrdinalIgnoreCase) -or $full.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Configuração, credenciais e backups devem ficar fora do repositório.'}
}
function Protect-MatiDirectory([string]$Path){
 if([IO.Path]::GetFullPath($Path).TrimEnd('\','/') -eq [IO.Path]::GetPathRoot([IO.Path]::GetFullPath($Path)).TrimEnd('\','/')){throw 'Não utilize a raiz de um disco como diretório privado.'}
 New-Item -ItemType Directory -Path $Path -Force|Out-Null
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 & icacls $Path /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F'|Out-Null
 if($LASTEXITCODE){throw 'Não foi possível restringir o diretório privado.'}
}
function Assert-MatiPrivate([string]$Path,[string]$Sid){
 if(!(Test-Path -LiteralPath $Path)){throw 'Arquivo privado indisponível.'}
 $acl=Get-Acl -LiteralPath $Path
 if($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $Sid){throw 'Proprietário Windows não autorizado.'}
 foreach($rule in $acl.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])){
  if($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin @($Sid,'S-1-5-18')){throw 'Permissões do arquivo privado são excessivas.'}
 }
}
function Invoke-MatiNative([string]$Exe,[string[]]$Arguments,[string]$InputText=''){
 $p=[Diagnostics.ProcessStartInfo]::new($Exe);$p.UseShellExecute=$false;$p.RedirectStandardOutput=$true;$p.RedirectStandardError=$true;$p.RedirectStandardInput=$true
 foreach($a in $Arguments){$p.ArgumentList.Add($a)}
 $process=[Diagnostics.Process]::Start($p);$outTask=$process.StandardOutput.ReadToEndAsync();$errTask=$process.StandardError.ReadToEndAsync()
 if($InputText){$process.StandardInput.Write($InputText)};$process.StandardInput.Close()
 if(!$process.WaitForExit(300000)){$process.Kill();throw 'Tempo de execução excedido; verifique o diagnóstico.'}
 $output=$outTask.GetAwaiter().GetResult();$null=$errTask.GetAwaiter().GetResult()
 if($process.ExitCode){throw 'Comando local recusado ou falhou. Verifique saúde, configuração e permissões; segredos não são exibidos.'}
 return $output.Trim()
}
function Docker($c,[string[]]$Arguments,[string]$InputText=''){Invoke-MatiNative $c.DockerPath (@('--context',$c.DockerContext)+$Arguments) $InputText}
function Compose($c,[string[]]$Arguments,[string]$InputText=''){
 $a=@('compose','--project-directory',$c.Repository,'-p',$c.Project);foreach($f in $c.ComposeFiles){$a+=@('-f',$f)}
 Docker $c ($a+$Arguments) $InputText
}
function Read-MatiConfig([string]$Path){
 if(!$IsWindows){throw 'A operação exige Windows e o usuário autorizado; use PowerShell 7.4 ou superior.'}
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;Assert-MatiPrivate $Path $sid
 $c=Get-Content -LiteralPath $Path -Raw|ConvertFrom-Json
 if($c.PSObject.Properties.Name -notcontains 'DockerContext'){$c|Add-Member -NotePropertyName DockerContext -NotePropertyValue 'desktop-linux'}
 if($c.DockerContext -ne 'desktop-linux'){throw 'Somente o Docker Desktop Linux local é autorizado.'}
 if($c.PSObject.Properties.Name -contains 'Repository'){Assert-MatiOutsideRepository $Path $c.Repository;Assert-MatiOutsideRepository $c.BackupRoot $c.Repository}
 if($c.OperatorSid -ne $sid){throw 'Operador Windows sem autorização para esta instalação.'}
 if($c.Project -notmatch '^[a-z0-9][a-z0-9_-]+$'){throw 'Projeto inválido.'}
 if($c.LabCredential){Assert-MatiPrivate $c.LabCredential $sid;$lab=Import-Clixml -LiteralPath $c.LabCredential;$env:MATI_LAB_PASSWORD=$lab.GetNetworkCredential().Password;$env:MATI_LAB_JWT=(Import-Clixml -LiteralPath $c.LabJwt).GetNetworkCredential().Password}
 $c|Add-Member -NotePropertyName ConfigurationFilePath -NotePropertyValue $Path -Force
 return $c
}
function Assert-MatiCompose($c){
 $effective=Compose $c @('config','--format','json')|ConvertFrom-Json
 if($effective.services.backend.environment.APP_SEED_DEMO_ENABLED -ne 'false'){throw 'Seed deve estar explicitamente desabilitado.'}
 if($effective.services.db.PSObject.Properties.Name -contains 'ports' -and @($effective.services.db.ports).Count){throw 'PostgreSQL não pode publicar portas.'}
 foreach($service in @('backend','frontend')){foreach($port in $effective.services.$service.ports){if($port.host_ip -ne '127.0.0.1'){throw 'Serviços devem publicar somente em 127.0.0.1.'}}}
 return $effective
}
function Get-MatiContainer($c,[string]$Service){
 $id=Compose $c @('ps','-a','-q',$Service)
 if(!$id -or $id.Contains("`n")){throw 'Serviço inexistente ou ambíguo.'}
 $state=@(Docker $c @('inspect',$id)|ConvertFrom-Json)[0]
 if($state.Config.Labels.'com.docker.compose.project' -ne $c.Project -or $state.Config.Labels.'com.docker.compose.service' -ne $Service){throw 'Identidade Compose divergente.'}
 foreach($port in $state.NetworkSettings.Ports.PSObject.Properties){foreach($binding in $port.Value){if($binding.HostIp -ne '127.0.0.1' -or $Service -eq 'db'){throw 'Publicação de porta não autorizada.'}}}
 if($Service -eq 'db'){$data=@($state.Mounts|Where-Object {$_.Destination -eq '/var/lib/postgresql/data' -and $_.Type -eq 'volume'});if($data.Count -ne 1){throw 'Volume PostgreSQL ausente ou ambíguo.'};$volume=@(Docker $c @('volume','inspect',$data[0].Name)|ConvertFrom-Json)[0];if($volume.Labels.'com.docker.compose.project' -ne $c.Project){throw 'Volume não pertence a este projeto Compose.'}}
 return $state
}
function Sql($c,[string]$Query){
 $db=Get-MatiContainer $c db;$vars=@{};foreach($e in $db.Config.Env){$p=$e.Split('=',2);$vars[$p[0]]=$p[1]}
 Docker $c @('exec','-i',$db.Id,'psql','-X','-U',$vars.POSTGRES_USER,'-d',$vars.POSTGRES_DB,'-At','-v','ON_ERROR_STOP=1') $Query
}
function Snapshot($c){
 $tables=[ordered]@{};$sequences=[ordered]@{}
 foreach($table in (Sql $c "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").Split("`n")){
  $table=$table.Trim();if($table -notmatch '^[a-z_]+$'){throw 'Identificador não suportado.'}
  $tables[$table]=Sql $c "SELECT count(*)::text||'|'||md5(COALESCE(string_agg(to_jsonb(t)::text,E'\n' ORDER BY to_jsonb(t)::text),'')) FROM $table t"
 }
 foreach($seq in (Sql $c "SELECT sequencename FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename").Split("`n")){
  $seq=$seq.Trim();if($seq -notmatch '^[a-z_]+$'){throw 'Sequência não suportada.'}
  $sequences[$seq]=Sql $c "SELECT last_value::text||'|'||is_called::text FROM $seq"
 }
 return @{Tables=$tables;Sequences=$sequences}
}
function Fingerprint($snapshot){
 $text='';foreach($key in @($snapshot.Tables.Keys|Sort-Object -CaseSensitive)){if($key -ne 'maintenance_audit_logs'){$text+=$key+':'+$snapshot.Tables[$key]+"`n"}}
 foreach($key in @($snapshot.Sequences.Keys|Sort-Object -CaseSensitive)){if($key -ne 'maintenance_audit_logs_id_seq'){$text+=$key+':'+$snapshot.Sequences[$key]+"`n"}}
 [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text))).ToLowerInvariant()
}
function New-MatiBackup($c){
 $timer=[Diagnostics.Stopwatch]::StartNew();$null=Assert-MatiCompose $c;$before=Snapshot $c
 $folder=Join-Path $c.BackupRoot ('backup-'+(Get-Date -Format yyyyMMdd-HHmmss)+'-'+[guid]::NewGuid().ToString('N').Substring(0,8));Protect-MatiDirectory $folder
 $db=Get-MatiContainer $c db;$vars=@{};foreach($e in $db.Config.Env){$p=$e.Split('=',2);$vars[$p[0]]=$p[1]}
 $start=[Diagnostics.ProcessStartInfo]::new($c.DockerPath);$start.UseShellExecute=$false;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 foreach($arg in @('--context',$c.DockerContext,'exec',$db.Id,'pg_dump','-U',$vars.POSTGRES_USER,'-d',$vars.POSTGRES_DB,'-Fc','--no-owner','--no-acl')){$start.ArgumentList.Add($arg)}
 $process=[Diagnostics.Process]::Start($start);$errors=$process.StandardError.ReadToEndAsync();$stream=[IO.File]::Create((Join-Path $folder database.dump));try{$process.StandardOutput.BaseStream.CopyTo($stream)}finally{$stream.Dispose()};$process.WaitForExit();$null=$errors.GetAwaiter().GetResult();if($process.ExitCode){throw 'Backup falhou; arquivo não deve ser utilizado.'}
 $after=Snapshot $c;if((Fingerprint $before) -ne (Fingerprint $after)){throw 'Dados mudaram durante o backup. Repita em janela sem gravações.'}
 $effective=Assert-MatiCompose $c
 $manifest=@{Format=1;CreatedAt=[DateTime]::UtcNow.ToString('o');Project=$c.Project;Database=$vars.POSTGRES_DB;SystemId=(Sql $c 'SELECT system_identifier::text FROM pg_control_system()');Tables=$before.Tables;Sequences=$before.Sequences;Fingerprint=(Fingerprint $before);Sha256=(Get-FileHash (Join-Path $folder database.dump)).Hash.ToLowerInvariant();BackendImage=(Get-MatiContainer $c backend).Config.Image;BackendImageId=(Get-MatiContainer $c backend).Image;FrontendImageId=(Get-MatiContainer $c frontend).Image;WorkingTreeDirty=[bool](Invoke-MatiNative 'git' @('-C',$c.Repository,'status','--porcelain'));RestoreBackendImage=$effective.services.backend.image;FrontendImage=$effective.services.frontend.image;ApplicationCommit=(Invoke-MatiNative 'git' @('-C',$c.Repository,'rev-parse','HEAD'));Migrations=(Sql $c 'SELECT json_agg(row_to_json(t) ORDER BY installed_rank)::text FROM flyway_schema_history t');RestoreVerified=$false;BackupSeconds=$timer.Elapsed.TotalSeconds;Requirements=@('JWT_SECRET separado do backup','Configuração e credencial protegidas do operador','Docker e imagens compatíveis','PostgreSQL 15')}
 $manifest|ConvertTo-Json -Depth 12|Set-Content -LiteralPath (Join-Path $folder manifest.json)
 return $folder
}
function Test-MatiBackup([string]$Folder){
 $manifest=Get-Content -LiteralPath (Join-Path $Folder manifest.json) -Raw|ConvertFrom-Json -AsHashtable
 if($manifest.Format -ne 1 -or (Get-FileHash (Join-Path $Folder database.dump)).Hash.ToLowerInvariant() -ne $manifest.Sha256){throw 'Backup ausente, adulterado ou incompatível.'}
 $stream=[IO.File]::OpenRead((Join-Path $Folder database.dump));try{$head=[byte[]]::new(5);$null=$stream.Read($head,0,5)}finally{$stream.Dispose()};if([Text.Encoding]::ASCII.GetString($head) -ne 'PGDMP'){throw 'Formato de backup inválido.'}
 return $manifest
}
function New-MatiLab($c,[string]$Folder,[string]$RestoreBackendImage=''){
 $timer=[Diagnostics.Stopwatch]::StartNew();$m=Test-MatiBackup $Folder;if($RestoreBackendImage){if($RestoreBackendImage -notmatch '^[a-zA-Z0-9_./:-]+$'){throw 'Imagem inválida.'};$null=Docker $c @('image','inspect',$RestoreBackendImage);$m.RestoreBackendImage=$RestoreBackendImage};$name='mati-lab-'+[guid]::NewGuid().ToString('N').Substring(0,12);$private=Join-Path $c.PrivateDirectory $name;Protect-MatiDirectory $private
 $secret=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36));$env:MATI_LAB_PASSWORD=$secret
 [pscredential]::new('postgres',(ConvertTo-SecureString $secret -AsPlainText -Force))|Export-Clixml (Join-Path $private lab.credential.xml)
 $env:MATI_LAB_JWT=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
 [pscredential]::new('jwt',(ConvertTo-SecureString $env:MATI_LAB_JWT -AsPlainText -Force))|Export-Clixml (Join-Path $private jwt.credential.xml)
 $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start();$frontPort=$listener.LocalEndpoint.Port;$listener.Stop()
 $compose=@'
services:
  db:
    image: postgres:15-alpine
    labels: {meu-assistente-ti.role: recovery-lab}
    environment:
      POSTGRES_USER: postgres
      POSTGRES_DB: helpdesk
      POSTGRES_PASSWORD: \${MATI_LAB_PASSWORD:?private credential required}
    volumes: [data:/var/lib/postgresql/data]
    healthcheck:
      test: [CMD-SHELL, pg_isready -U postgres -d helpdesk]
      interval: 2s
      timeout: 3s
      retries: 30
  backend:
    image: __BACKEND__
    ports: [127.0.0.1::8080]
    environment:
      DB_URL: jdbc:postgresql://db:5432/helpdesk
      DB_USER: postgres
      DB_PASSWORD: \${MATI_LAB_PASSWORD:?private credential required}
      JWT_SECRET: \${MATI_LAB_JWT:?private credential required}
      CORS_ALLOWED_ORIGINS: http://127.0.0.1:__FRONTEND_PORT__,http://localhost:__FRONTEND_PORT__
      SPRING_PROFILES_ACTIVE: prod
      APP_SEED_DEMO_ENABLED: 'false'
    depends_on:
      db: {condition: service_healthy}
  frontend:
    image: __FRONTEND__
    ports: [127.0.0.1:__FRONTEND_PORT__:80]
    depends_on: [backend]
volumes:
  data:
    labels: {meu-assistente-ti.role: recovery-lab}
'@
 # Literal interpolation belongs to Compose, never to a shell or an on-disk password.
 $compose=$compose.Replace('\$'+'{','${').Replace('__BACKEND__',$m.RestoreBackendImage).Replace('__FRONTEND__',$m.FrontendImage).Replace('__FRONTEND_PORT__',$frontPort);$file=Join-Path $private compose.yml;[IO.File]::WriteAllText($file,$compose)
 $lab=[pscustomobject]@{Repository=$c.Repository;Project=$name;ComposeFiles=@($file);DockerPath=$c.DockerPath;DockerContext=$c.DockerContext;OperatorSid=$c.OperatorSid;PrivateDirectory=$private;BackupRoot=(Join-Path $private backups);KeyFile=$c.KeyFile;TrustFile=$c.TrustFile;LabCredential=(Join-Path $private lab.credential.xml);LabJwt=(Join-Path $private jwt.credential.xml)}
 $null=Compose $lab @('up','-d','--wait','db');$db=Get-MatiContainer $lab db
 if($db.Config.Labels.'meu-assistente-ti.role' -ne 'recovery-lab' -or $db.Name -notlike ('/'+$name+'-*')){throw 'Destino de restauração não é laboratório identificado.'}
 foreach($mount in $db.Mounts){if($mount.Type -eq 'volume'){$volume=@(Docker $lab @('volume','inspect',$mount.Name)|ConvertFrom-Json)[0];if($volume.Labels.'meu-assistente-ti.role' -ne 'recovery-lab'){throw 'Volume de destino não autorizado.'}}}
 $null=Docker $lab @('cp',(Join-Path $Folder database.dump),($db.Id+':/tmp/restore.dump'))
 $null=Docker $lab @('exec',$db.Id,'pg_restore','-U','postgres','-d','helpdesk','--no-owner','--no-acl','/tmp/restore.dump')
 $restored=Snapshot $lab;foreach($key in $m.Tables.Keys){if($restored.Tables[$key] -ne $m.Tables[$key]){throw ('Tabela restaurada divergente: '+$key)}};foreach($key in $m.Sequences.Keys){if($restored.Sequences[$key] -ne $m.Sequences[$key]){throw 'Sequência restaurada divergente.'}}
 $null=Compose $lab @('up','-d','--no-build','--pull','never','backend','frontend');Wait-Mati $lab
 $upgraded=Snapshot $lab;foreach($key in $m.Tables.Keys){if($key -ne 'flyway_schema_history' -and $upgraded.Tables[$key] -ne $m.Tables[$key]){throw ('Aplicação mudou dados restaurados: '+$key)}}
 $oldMigrations=$m.Migrations|ConvertFrom-Json;$newMigrations=(Sql $lab 'SELECT json_agg(row_to_json(t) ORDER BY installed_rank)::text FROM flyway_schema_history t')|ConvertFrom-Json
 foreach($old in $oldMigrations){$new=@($newMigrations|Where-Object installed_rank -eq $old.installed_rank)[0];if(($old|ConvertTo-Json -Compress) -ne ($new|ConvertTo-Json -Compress)){throw 'Migração aplicada anteriormente foi alterada.'}}
 $labFile=Join-Path $private installation.json;$lab|ConvertTo-Json -Depth 8|Set-Content $labFile
 $m.RestoreVerified=$true;$m.RestoredAt=[DateTime]::UtcNow.ToString('o');$m.RestoreSeconds=$timer.Elapsed.TotalSeconds;$m.LabConfiguration=$labFile;$m|ConvertTo-Json -Depth 12|Set-Content (Join-Path $Folder manifest.json)
 return $labFile
}
function Wait-Mati($c){for($i=0;$i -lt 90;$i++){try{$backend=Get-MatiContainer $c backend;$port=($backend.NetworkSettings.Ports.'8080/tcp'|Select-Object -First 1).HostPort;$r=Invoke-WebRequest "http://127.0.0.1:$port/tickets" -SkipHttpErrorCheck -TimeoutSec 2;if($r.StatusCode -eq 401){return}}catch{};Start-Sleep -Milliseconds 500};throw 'Backend não ficou disponível.'}
function Recover-Mati($c,[long]$UserId,[string]$Email,[string]$Reason,[string]$NewCredentialFile){
 Assert-MatiPrivate $c.KeyFile $c.OperatorSid;Assert-MatiPrivate $c.TrustFile $c.OperatorSid
 $journal=Join-Path $c.PrivateDirectory 'maintenance-journal.jsonl';$result='REFUSED';$committed=$false
 $mutex=[Threading.Mutex]::new($false,('Local\MeuAssistenteTI-maintenance-'+$c.Project));$acquired=$false
 try{$acquired=$mutex.WaitOne(0);if(!$acquired){throw 'Outra manutenção está em execução.'}
 if($UserId -le 0 -or !$Email -or !$Reason -or $Reason.Length -gt 255){throw 'Informe ID, e-mail exato e motivo de até 255 caracteres.'}
 if(!$NewCredentialFile -or (Test-Path -LiteralPath $NewCredentialFile)){throw 'Escolha um arquivo DPAPI novo; nenhuma credencial existente será sobrescrita.'}
 Assert-MatiOutsideRepository $NewCredentialFile $c.Repository
 $credentialDirectory=Split-Path -Parent $NewCredentialFile;Protect-MatiDirectory $credentialDirectory;Assert-MatiPrivate $credentialDirectory $c.OperatorSid
 $null=Assert-MatiCompose $c;$backend=Get-MatiContainer $c backend
 if($backend.State.Running){throw 'Pare somente o backend desta instalação antes da manutenção e preserve o PostgreSQL ativo.'}
 $rows=Sql $c "SELECT json_agg(row_to_json(t))::text FROM (SELECT id,email,role,active,session_version,edit_version FROM users WHERE id=$UserId) t"
 if(!$rows){throw 'Conta de destino inexistente.'};$user=@($rows|ConvertFrom-Json)[0]
 if($user.email -cne $Email -or $user.role -ne 'ADMIN' -or !$user.active){throw 'Destino não corresponde exatamente a um ADMIN ativo.'}
 $backup=New-MatiBackup $c;$null=New-MatiLab $c $backup;$null=Read-MatiConfig $c.ConfigurationFilePath
 $m=Test-MatiBackup $backup;if(!$m.RestoreVerified){throw 'Backup não restaurado e validado.'}
 $password='Aa1!'+[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
 $credential=[pscredential]::new($Email,(ConvertTo-SecureString $password -AsPlainText -Force));$credential|Export-Clixml -LiteralPath $NewCredentialFile -NoClobber
 # Verify DPAPI before any persistent credential change. If later steps fail, keep this protected candidate for diagnosis.
 $candidate=Import-Clixml -LiteralPath $NewCredentialFile;if($candidate.GetNetworkCredential().Password -cne $password){throw 'DPAPI não foi validado.'}
 $payload=@{operation='recover-admin';requestId=[guid]::NewGuid().ToString();issuedAt=[DateTime]::UtcNow.ToString('o');operatorSid=$c.OperatorSid;operatorName=[Security.Principal.WindowsIdentity]::GetCurrent().Name;database=$m.Database;systemId=$m.SystemId;installationId=(Sql $c 'SELECT installation_id::text FROM maintenance_installation WHERE id=1');backupSha256=$m.Sha256;fingerprint=$m.Fingerprint;restoreVerified=$true;userId=$UserId;email=$Email;sessionVersion=$user.session_version;editVersion=$user.edit_version;password=$password;reason=$Reason}
 $bytes=[Text.Encoding]::UTF8.GetBytes(($payload|ConvertTo-Json -Compress));$rsa=[Security.Cryptography.RSA]::Create();$key=Import-Clixml -LiteralPath $c.KeyFile;$rsa.ImportPkcs8PrivateKey([Convert]::FromBase64String($key.GetNetworkCredential().Password),[ref]$null)
 try{$signature=$rsa.SignData($bytes,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1);$envelope=@{payload=[Convert]::ToBase64String($bytes);signature=[Convert]::ToBase64String($signature)}|ConvertTo-Json -Compress
  $args=@('run','--rm','--no-deps','-T','-e','MAINTENANCE_TRUST_FILE=/run/maintenance/trust.properties','-e','MAINTENANCE_BACKUP_FILE=/run/maintenance/backup.dump','-v',($c.TrustFile+':/run/maintenance/trust.properties:ro'),'-v',((Join-Path $backup database.dump)+':/run/maintenance/backup.dump:ro'),'backend','recover-admin')
  $null=Compose $c $args $envelope;$committed=$true
 }finally{$rsa.Dispose();[Array]::Clear($bytes);$key=$null;$payload=$null;$envelope=$null;$password=$null;$candidate=$null}
 $null=Compose $c @('start','backend');Wait-Mati $c;$null=Compose $c @('restart','frontend')
 $backend=Get-MatiContainer $c backend;$port=($backend.NetworkSettings.Ports.'8080/tcp'|Select-Object -First 1).HostPort
 $new=Import-Clixml -LiteralPath $NewCredentialFile
 try{$login=Invoke-RestMethod "http://127.0.0.1:$port/auth/login" -Method Post -ContentType application/json -Body ([Text.Encoding]::UTF8.GetBytes((@{email=$new.UserName;password=$new.GetNetworkCredential().Password}|ConvertTo-Json -Compress)));$all=Invoke-RestMethod "http://127.0.0.1:$port/users" -Headers @{Authorization='Bearer '+$login.token};if(!@($all|Where-Object {$_.id -eq $UserId -and $_.role -eq 'ADMIN' -and $_.active}).Count){throw 'Perfil administrativo não validado.'}}
 catch{throw 'Manutenção requer atenção: não remova a nova credencial DPAPI. A validação do acesso não concluiu.'}
 finally{$new=$null;$login=$null;$credential=$null}
 $result='SUCCESS'
 [pscustomobject]@{Recovery='SUCCESS';AdminLogin=200;AdministrativeAccess=200;CredentialFile=$NewCredentialFile;BackupDirectory=$backup}
 }finally{try{@{Utc=[DateTime]::UtcNow.ToString('o');OperatorSid=$c.OperatorSid;OperatorName=[Security.Principal.WindowsIdentity]::GetCurrent().Name;UserId=$UserId;Reason=$Reason;Result=$(if($committed -and $result -ne 'SUCCESS'){'NEEDS_ATTENTION'}else{$result})}|ConvertTo-Json -Compress|Add-Content -LiteralPath $journal}finally{if($acquired){$mutex.ReleaseMutex()};$mutex.Dispose()}}
}
function Invoke-MatiOperation {
 param([string]$Action,[string]$Config,[string]$Repository,[string]$OperationalOverride,[string]$BackupRoot,[string]$BackupDirectory,[string]$RestoreBackendImage,[long]$UserId,[string]$Email,[string]$Reason,[string]$NewCredentialFile)
 if($Action -eq 'Initialize'){
  if(Test-Path -LiteralPath $Config){$null=Read-MatiConfig $Config;'Configuração existente validada; não sobrescrita.';return}
  if(!$IsWindows -or !$Repository -or !$OperationalOverride -or !$BackupRoot -or ![IO.Path]::IsPathFullyQualified($Config)){throw 'Inicialização exige Windows e caminhos absolutos de instalação.'}
  Assert-MatiOutsideRepository $Config $Repository;Assert-MatiOutsideRepository $BackupRoot $Repository
  $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;$envFile=Join-Path $Repository '.env';if(!(Test-Path $envFile) -or (Get-Acl $envFile).GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid){throw 'Inicialização exige o proprietário Windows da configuração privada da instalação.'};$private=Split-Path -Parent $Config;Protect-MatiDirectory $private
  $rsa=[Security.Cryptography.RSA]::Create(3072);$keyFile=Join-Path $private maintenance-key.credential.xml;$trustFile=Join-Path $private trust.properties
  if((Test-Path $keyFile) -or (Test-Path $trustFile)){Assert-MatiPrivate $keyFile $sid;Assert-MatiPrivate $trustFile $sid;$existing=Import-Clixml $keyFile;$rsa.ImportPkcs8PrivateKey([Convert]::FromBase64String($existing.GetNetworkCredential().Password),[ref]$null);$existing=$null;$trusted=Get-Content $trustFile;if($trusted -notcontains "operatorSid=$sid" -or $trusted -notcontains "publicKey=$([Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()))"){$rsa.Dispose();throw 'Chave parcial ou operador divergente. Preserve os arquivos e investigue.'};$rsa.Dispose()}else{
  try{[pscredential]::new('maintenance-signing-key',(ConvertTo-SecureString ([Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey())) -AsPlainText -Force))|Export-Clixml $keyFile;"operatorSid=$sid`npublicKey=$([Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()))"|Set-Content $trustFile}finally{$rsa.Dispose()}
  }
  $found=Get-Command docker.exe -CommandType Application -ErrorAction SilentlyContinue;$docker=if($found){$found.Source}else{''};if(!$docker){$docker=Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'};if(!(Test-Path $docker)){throw 'Docker Desktop não localizado.'}
  $override=Join-Path $private operational.yml;Copy-Item -LiteralPath $OperationalOverride -Destination $override
  $c=[pscustomobject]@{Repository=$Repository;Project='helpdesk-pro';ComposeFiles=@((Join-Path $Repository docker-compose.yml),$override);DockerPath=$docker;DockerContext='desktop-linux';OperatorSid=$sid;PrivateDirectory=$private;BackupRoot=$BackupRoot;KeyFile=$keyFile;TrustFile=$trustFile;LabCredential='';LabJwt=''}
  $null=Assert-MatiCompose $c;$c|ConvertTo-Json -Depth 8|Set-Content $Config;'Configuração privada e chave DPAPI criadas para o operador Windows atual.';return
 }
 $c=Read-MatiConfig $Config
 if($Action -eq 'Start'){
  try{$null=Docker $c @('info','--format','{{.ServerVersion}}')}catch{$desktop=Join-Path (Split-Path (Split-Path (Split-Path $c.DockerPath))) 'Docker Desktop.exe';Start-Process -FilePath $desktop -WindowStyle Hidden;for($i=0;$i -lt 90;$i++){Start-Sleep -Milliseconds 500;try{$null=Docker $c @('info','--format','{{.ServerVersion}}');break}catch{}}}
 }
 $null=Assert-MatiCompose $c
 switch($Action){
  Start {$dbId=Compose $c @('ps','-a','-q','db');if($dbId){$db=Get-MatiContainer $c db;if(!$db.State.Running){$null=Compose $c @('start','db')}}else{$null=Compose $c @('up','-d','--no-build','--pull','never','db')};$dbReady=$false;for($i=0;$i -lt 90;$i++){$db=Get-MatiContainer $c db;if($db.State.Health.Status -eq 'healthy'){$dbReady=$true;break};Start-Sleep -Milliseconds 500};if(!$dbReady){throw 'PostgreSQL não ficou saudável.'};$null=Compose $c @('up','-d','--no-deps','--no-build','--pull','never','backend','frontend');Wait-Mati $c;$null=Compose $c @('restart','frontend');'Serviços iniciados localmente; seed desabilitado.'}
  MaintenanceStop {$null=Compose $c @('stop','backend');'Backend parado para manutenção; banco preservado.'}
  Stop {$null=Compose $c @('stop','frontend','backend','db');'Serviços parados. Contêineres e volumes preservados.'}
  {$_ -in @('Health','Diagnose')} {
   $result=@();foreach($service in @('db','backend','frontend')){$s=Get-MatiContainer $c $service;$health=if($s.State.PSObject.Properties.Name -contains 'Health'){$s.State.Health.Status}else{$s.State.Status};$result+=[pscustomobject]@{Service=$service;Container=$s.Name.TrimStart('/');State=$health;Image=$s.Config.Image;VolumeNames=@($s.Mounts|Where-Object Type -eq volume|ForEach-Object Name)}}
   $result
   if($Action -eq 'Diagnose'){Sql $c "SELECT json_build_object('flywayVersions',(SELECT count(*) FROM flyway_schema_history WHERE success),'users',(SELECT count(*) FROM users),'activeAdmins',(SELECT count(*) FROM users WHERE role='ADMIN' AND active),'tickets',(SELECT count(*) FROM tickets),'assets',(SELECT count(*) FROM assets),'comments',(SELECT count(*) FROM ticket_comments),'ticketAudits',(SELECT count(*) FROM ticket_audit_logs))::text"}
   if($Action -eq 'Diagnose' -and (Sql $c "SELECT to_regclass('public.maintenance_audit_logs') IS NOT NULL") -eq 't'){Sql $c "SELECT COALESCE(json_agg(row_to_json(r) ORDER BY id DESC),'[]'::json)::text FROM (SELECT id,request_id,operator_name,target_user_id,result,result_code,occurred_at FROM maintenance_audit_logs ORDER BY id DESC LIMIT 20) r"}
   if($Action -eq 'Health'){if(@($result|Where-Object {$_.State -notin @('running','healthy')}).Count){throw 'Existem serviços indisponíveis.'};Wait-Mati $c;$front=Get-MatiContainer $c frontend;$port=($front.NetworkSettings.Ports.'80/tcp'|Select-Object -First 1).HostPort;$null=Invoke-WebRequest "http://127.0.0.1:$port/login" -TimeoutSec 10;$proxy=Invoke-WebRequest "http://127.0.0.1:$port/api/tickets" -Headers @{Origin="http://127.0.0.1:$port"} -SkipHttpErrorCheck -TimeoutSec 10;if($proxy.StatusCode -ne 401){throw 'Proxy ou CORS da instalação não está pronto.'};'API protegida, frontend acessível, CORS validado e seed=false.'}
  }
  Backup {$folder=New-MatiBackup $c;[pscustomobject]@{BackupDirectory=$folder;Verified=$true}}
  VerifyBackup {$m=Test-MatiBackup $BackupDirectory;[pscustomobject]@{HashValid=$true;RestoreVerified=$m.RestoreVerified;CreatedAt=$m.CreatedAt;ApplicationCommit=$m.ApplicationCommit}}
  RestoreLab {$labFile=New-MatiLab $c $BackupDirectory $RestoreBackendImage;[pscustomobject]@{Restored=$true;Configuration=$labFile;MainOverwritten=$false}}
  RecoverAdmin {Recover-Mati $c $UserId $Email $Reason $NewCredentialFile}
 }
}
Export-ModuleMember -Function Invoke-MatiOperation,Test-MatiBackup
