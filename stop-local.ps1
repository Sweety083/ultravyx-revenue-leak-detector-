param([switch]$BackendOnly)
$ErrorActionPreference = 'Stop'
$runtimeDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.local-runtime'))

function Stop-ManagedProcess([string]$Name, [string]$ExpectedCommand) {
    $pidFile = Join-Path $runtimeDir ($Name + '.pid')
    if (!(Test-Path $pidFile)) { return }
    $processId = [int](Get-Content $pidFile)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
    if ($process) {
        if (!$process.CommandLine -or !$process.CommandLine.Contains($ExpectedCommand)) {
            throw "Refusing to stop process $processId because it no longer matches this project's $Name service."
        }
        Stop-Process -Id $processId
        Wait-Process -Id $processId -Timeout 20 -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $pidFile
}
Stop-ManagedProcess 'backend' (Join-Path $PSScriptRoot 'backend/target/revenue-leak-detector-0.1.0.jar')
if (!$BackendOnly) {
    Stop-ManagedProcess 'frontend' (Join-Path $PSScriptRoot 'frontend/node_modules/@angular/cli/bin/ng.js')
    $dataDir = Join-Path $runtimeDir 'pgdata'
    $pgCtl = Join-Path $runtimeDir 'postgresql/pgsql/bin/pg_ctl.exe'
    if (Test-Path (Join-Path $dataDir 'postmaster.pid')) {
        & $pgCtl -D $dataDir -m fast -w stop
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL did not shut down successfully.' }
    }
}
Write-Host 'Requested services stopped. Imported data is preserved.'
