#Requires -Version 7.4
$ErrorActionPreference='Stop'
$modulePath=Join-Path $PSScriptRoot '../MeuAssistenteTI.Operations.psm1'
$passed=0
function Check([bool]$Condition,[string]$Name){if(!$Condition){throw "Falha: $Name"};$script:passed++}
foreach($p in @($modulePath,(Join-Path $PSScriptRoot '../MeuAssistenteTI.ps1'))){$errors=$null;$tokens=$null;[Management.Automation.Language.Parser]::ParseFile((Resolve-Path $p),[ref]$tokens,[ref]$errors)|Out-Null;Check (@($errors).Count -eq 0) 'Sintaxe PowerShell'}
Import-Module $modulePath -Force
$folder=Join-Path ([IO.Path]::GetTempPath()) ('mati-ops-test-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory $folder|Out-Null
$dump=Join-Path $folder database.dump;[IO.File]::WriteAllBytes($dump,[Text.Encoding]::ASCII.GetBytes('PGDMP-test-only'))
$manifest=@{Format=1;Sha256=(Get-FileHash $dump).Hash.ToLowerInvariant();RestoreVerified=$false};$manifest|ConvertTo-Json|Set-Content (Join-Path $folder manifest.json)
Check (!(Test-MatiBackup $folder).RestoreVerified) 'Hash não equivale a restauração validada'
$manifest.Sha256='0'*64;$manifest|ConvertTo-Json|Set-Content (Join-Path $folder manifest.json);$refused=$false;try{Test-MatiBackup $folder|Out-Null}catch{$refused=$true};Check $refused 'Hash adulterado recusado'
$manifest.Sha256=(Get-FileHash $dump).Hash.ToLowerInvariant();$manifest.Format=9;$manifest|ConvertTo-Json|Set-Content (Join-Path $folder manifest.json);$refused=$false;try{Test-MatiBackup $folder|Out-Null}catch{$refused=$true};Check $refused 'Versão desconhecida recusada'
$manifest.Format=1;[IO.File]::WriteAllText($dump,'not-a-postgres-dump');$manifest.Sha256=(Get-FileHash $dump).Hash.ToLowerInvariant();$manifest|ConvertTo-Json|Set-Content (Join-Path $folder manifest.json);$refused=$false;try{Test-MatiBackup $folder|Out-Null}catch{$refused=$true};Check $refused 'Formato falso recusado'
$refused=$false;try{Test-MatiBackup (Join-Path $folder absent)|Out-Null}catch{$refused=$true};Check $refused 'Backup inexistente recusado'
if($IsWindows){
 $module=Get-Module MeuAssistenteTI.Operations
 & $module {param($f) Protect-MatiDirectory $f} $folder
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 $key=Join-Path $folder key.xml;$trust=Join-Path $folder trust.properties;'test-key-only'|Set-Content $key;'test-trust-only'|Set-Content $trust
 $configPath=Join-Path $folder installation.json
 $config=@{OperatorSid=$sid;Project='mati-test';KeyFile=$key;TrustFile=$trust;LabCredential='';LabJwt=''};$config|ConvertTo-Json|Set-Content $configPath
 foreach($owned in @($folder,$key,$trust,$configPath)){& icacls $owned /setowner "*$sid"|Out-Null}
 $read=& $module {param($f) Read-MatiConfig $f} $configPath;Check ($read.OperatorSid -eq $sid) 'Operador proprietário autorizado'
 $config.OperatorSid='S-1-5-21-999';$config|ConvertTo-Json|Set-Content $configPath;$refused=$false;try{& $module {param($f) Read-MatiConfig $f} $configPath|Out-Null}catch{$refused=$_.Exception.Message -like '*sem autorização*'};Check $refused 'SID divergente recusado antes de acessar Docker'
 $config.OperatorSid=$sid;$config|ConvertTo-Json|Set-Content $configPath
 & icacls $configPath /grant '*S-1-1-0:R'|Out-Null;$refused=$false;try{& $module {param($f) Read-MatiConfig $f} $configPath|Out-Null}catch{$refused=$_.Exception.Message -like '*excessivas*'};Check $refused 'ACL excessiva recusada'
}
[pscustomobject]@{ChecksPassed=$passed;WindowsAuthorizationChecked=[bool]$IsWindows;Failed=0}|ConvertTo-Json
# Intentionally no recursive deletion: temporary, synthetic fixtures contain no credentials.
