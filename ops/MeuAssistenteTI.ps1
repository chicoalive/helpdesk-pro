#Requires -Version 7.4
[CmdletBinding()]
param(
 [Parameter(Mandatory)][ValidateSet('Initialize','Start','Health','Stop','MaintenanceStop','Diagnose','Backup','VerifyBackup','RestoreLab','RecoverAdmin')][string]$Action,
 [Parameter(Mandatory)][string]$Config,
 [string]$Repository,[string]$OperationalOverride,[string]$BackupRoot,[string]$BackupDirectory,[string]$RestoreBackendImage,
 [long]$UserId,[string]$Email,[string]$Reason,[string]$NewCredentialFile
)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'MeuAssistenteTI.Operations.psm1') -Force
try {
 Invoke-MatiOperation @PSBoundParameters
 exit 0
} catch {
 # Exceptions from external commands are intentionally not printed: they may contain private SQL or environment.
 Write-Error ('Operação não concluída. '+$_.Exception.Message) -ErrorAction Continue
 exit 1
} finally {
 foreach($name in @('MATI_LAB_PASSWORD','MATI_LAB_JWT')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
}
