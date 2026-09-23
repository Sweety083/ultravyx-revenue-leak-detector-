param([switch]$Build)
$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$runtimeDir = [IO.Path]::GetFullPath((Join-Path $projectDir '../.local-runtime'))
$javaDir = Get-ChildItem (Join-Path $runtimeDir 'java') -Directory | Select-Object -First 1
if (!$javaDir) { throw 'Project-local Java is missing. Install JDK 21 and follow the README startup instructions.' }
$javaExe = Join-Path $javaDir.FullName 'bin/java.exe'
$pgBin = Join-Path $runtimeDir 'postgresql/pgsql/bin'
$dataDir = Join-Path $runtimeDir 'pgdata'
$logsDir = Join-Path $runtimeDir 'logs'
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

function Test-LocalPort([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1', $Port); return $true } catch { return $false } finally { $client.Dispose() }
}
function Wait-Http([string]$Url, [int]$Seconds = 90) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try { $response = Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 3; if ($response.StatusCode -eq 200) { return } } catch {}
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Service did not become ready at $Url. Check logs in $logsDir."
}

$originalJava = $env:JAVA_HOME
$originalMaven = $env:MAVEN_USER_HOME
$originalPgPassword = $env:PGPASSWORD
try {
    $env:JAVA_HOME = $javaDir.FullName
    $env:MAVEN_USER_HOME = Join-Path $runtimeDir 'maven'
    $env:PGPASSWORD = 'ultravyx_dev'
    if (!(Test-Path (Join-Path $dataDir 'PG_VERSION'))) {
        if (Test-LocalPort 5432) { throw 'Port 5432 is already occupied. Use the existing PostgreSQL server with the README instructions.' }
        $passwordFile = Join-Path $runtimeDir 'init-password.txt'
        [IO.File]::WriteAllText($passwordFile, "ultravyx_dev`n", [Text.UTF8Encoding]::new($false))
        try {
            & (Join-Path $pgBin 'initdb.exe') -D $dataDir -U ultravyx --encoding=UTF8 --locale=C --auth=scram-sha-256 --pwfile=$passwordFile
            if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initialization failed.' }
        } finally { Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue }
    }
    if (!(Test-LocalPort 5432)) {
        $pgLog = Join-Path $logsDir 'postgresql.log'
        $pg = Start-Process -FilePath (Join-Path $pgBin 'pg_ctl.exe') -ArgumentList @('-D', ('"' + $dataDir + '"'), '-l', ('"' + $pgLog + '"'), '-o', '"-h 127.0.0.1 -p 5432"', '-w', 'start') -WindowStyle Hidden -PassThru
        # Start-Process -Wait also waits for postgres descendants, which stay alive as the server.
        if (!$pg.WaitForExit(60000)) { throw "PostgreSQL startup timed out. Check $pgLog." }
        if ($pg.ExitCode -ne 0) { throw "PostgreSQL startup failed. Check $pgLog." }
    }
    $exists = & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -U ultravyx -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='ultravyx'"
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect to PostgreSQL using the project credentials.' }
    if ($exists -ne '1') {
        & (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -U ultravyx ultravyx
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the ultravyx database.' }
    }

    $jarPath = Join-Path $projectDir 'backend/target/revenue-leak-detector-0.1.0.jar'
    if ($Build -or !(Test-Path $jarPath)) {
        if (Test-LocalPort 8080) {
            & (Join-Path $projectDir 'stop-local.ps1') -BackendOnly
            if (Test-LocalPort 8080) { throw 'Port 8080 belongs to another process. Stop it before rebuilding.' }
        }
        Push-Location (Join-Path $projectDir 'backend')
        try {
            & ./mvnw.cmd -B --no-transfer-progress ("-Dmaven.repo.local=" + (Join-Path $runtimeDir 'm2')) package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
        } finally { Pop-Location }
    }
    if (!(Test-LocalPort 8080)) {
        $api = Start-Process -FilePath $javaExe -ArgumentList @('-jar', ('"' + $jarPath + '"'), '--server.address=127.0.0.1', '--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ultravyx', '--spring.datasource.username=ultravyx', '--spring.datasource.password=ultravyx_dev') -WorkingDirectory $projectDir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logsDir 'backend.log') -RedirectStandardError (Join-Path $logsDir 'backend-error.log') -PassThru
        $api.Id | Set-Content (Join-Path $runtimeDir 'backend.pid')
    }
    Wait-Http 'http://127.0.0.1:8080/api/dashboard/summary'
    if (!(Test-LocalPort 4200)) {
        $nodeExe = (Get-Command node.exe -ErrorAction Stop).Source
        $frontendDir = Join-Path $projectDir 'frontend'
        $ng = Join-Path $frontendDir 'node_modules/@angular/cli/bin/ng.js'
        if (!(Test-Path $ng)) { throw 'Frontend dependencies are missing. Run npm install in frontend first.' }
        $ui = Start-Process -FilePath $nodeExe -ArgumentList @(('"' + $ng + '"'), 'serve', '--host', '127.0.0.1', '--proxy-config', 'proxy.conf.json') -WorkingDirectory $frontendDir -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logsDir 'frontend.log') -RedirectStandardError (Join-Path $logsDir 'frontend-error.log') -PassThru
        $ui.Id | Set-Content (Join-Path $runtimeDir 'frontend.pid')
    }
    Wait-Http 'http://127.0.0.1:4200/api/dashboard/summary'
    Write-Host 'Ready: http://127.0.0.1:4200/upload'
    Write-Host "Database persists in $dataDir. Logs: $logsDir"
} finally {
    $env:JAVA_HOME = $originalJava
    $env:MAVEN_USER_HOME = $originalMaven
    $env:PGPASSWORD = $originalPgPassword
}
