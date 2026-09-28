# Runs the packaged JAR against isolated PostgreSQL with real, certificate-verified TLS.
[CmdletBinding()]
param([string]$JarPath = (Join-Path $PSScriptRoot 'target/TaskManager-0.0.1-SNAPSHOT.jar'))

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot
$composeFile = Join-Path $repoRoot 'docker-compose.yml'
$jar = (Resolve-Path -LiteralPath $JarPath).Path
$runId = 'taskmanager-smoke-' + [Guid]::NewGuid().ToString('N')
$runDir = Join-Path $PSScriptRoot ('target/' + $runId)
New-Item -ItemType Directory -Path $runDir | Out-Null
$app = $null
$composeStarted = $false
$savedEnvironment = @{}
foreach ($name in @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'POSTGRES_PORT')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}

function Invoke-NativeTool {
    param([string]$File, [string[]]$Arguments, [switch]$AllowFailure)
    # Windows PowerShell otherwise treats native progress written to stderr as an error.
    $ErrorActionPreference = 'Continue'
    & $File @Arguments 2>&1 | ForEach-Object { $_.ToString() }
    if ($LASTEXITCODE -ne 0 -and !$AllowFailure) { throw "$File failed with exit code $LASTEXITCODE." }
}

function Invoke-SmokeRequest {
    param([string]$Method, [string]$Path, [int]$Expected, [object]$Body = $null, [string]$Token = '')
    $responseFile = Join-Path $runDir 'response.json'
    $curlArgs = @('--silent', '--show-error', '--connect-timeout', '3', '--max-time', '10',
        '--noproxy', '*', '--cacert', $certificate, '--output', $responseFile,
        '--write-out', '%{http_code}', '--request', $Method, ($baseUrl + $Path))
    if ($Body) {
        $requestFile = Join-Path $runDir 'request.json'
        [IO.File]::WriteAllText($requestFile, ($Body | ConvertTo-Json -Compress))
        $curlArgs += @('--header', 'Content-Type: application/json', '--data-binary', ('@' + $requestFile))
    }
    if ($Token) {
        # Keep bearer credentials out of process command-line arguments.
        $headerFile = Join-Path $runDir 'authorization.txt'
        [IO.File]::WriteAllText($headerFile, 'Authorization: Bearer ' + $Token)
        $curlArgs += @('--header', ('@' + $headerFile))
    }
    $code = Invoke-NativeTool 'curl.exe' $curlArgs
    if ([int]$code -ne $Expected) {
        throw "$Method $Path expected $Expected, received $code. See application logs in $runDir."
    }
    if ($Expected -ne 204) { Get-Content -Raw -LiteralPath $responseFile | ConvertFrom-Json }
}

try {
    foreach ($tool in @('docker', 'java', 'keytool', 'curl.exe')) { Get-Command $tool -ErrorAction Stop | Out-Null }
    $env:POSTGRES_DB = 'taskmanager_smoke'
    $env:POSTGRES_USER = 'taskmanager_smoke'
    $env:POSTGRES_PASSWORD = New-Secret
    $env:POSTGRES_PORT = '0'
    $composeStarted = $true
    Invoke-NativeTool 'docker' @('compose', '--project-name', $runId, '--file', $composeFile, 'up', '-d', '--wait', '--wait-timeout', '120')
    $binding = Invoke-NativeTool 'docker' @('compose', '--project-name', $runId, '--file', $composeFile, 'port', 'postgres', '5432')
    if ($binding -notmatch ':(\d+)\s*$') { throw 'Cannot resolve PostgreSQL port.' }
    $databasePort = $Matches[1]

    $store = Join-Path $runDir 'localhost.p12'
    $certificate = Join-Path $runDir 'localhost.pem'
    $storePasswordFile = Join-Path $runDir 'keystore-password.txt'
    $storePassword = New-Secret
    [IO.File]::WriteAllText($storePasswordFile, $storePassword)
    Invoke-NativeTool 'keytool' @('-genkeypair', '-alias', 'localhost', '-keyalg', 'RSA', '-keysize', '2048',
        '-storetype', 'PKCS12', '-keystore', $store, '-storepass:file', $storePasswordFile,
        '-dname', 'CN=localhost', '-ext', 'SAN=dns:localhost,ip:127.0.0.1', '-validity', '2', '-noprompt')
    Invoke-NativeTool 'keytool' @('-exportcert', '-rfc', '-alias', 'localhost', '-keystore', $store,
        '-storepass:file', $storePasswordFile, '-file', $certificate)

    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $appPort = $listener.LocalEndpoint.Port
    $listener.Stop()
    $baseUrl = "https://localhost:$appPort"
    $config = Join-Path $runDir 'smoke.properties'
    $storeUri = ([Uri]$store).AbsoluteUri
    $settings = @(
        'spring.profiles.active=prod', 'server.address=127.0.0.1', "server.port=$appPort",
        'server.ssl.enabled=true', "server.ssl.key-store=$storeUri", 'server.ssl.key-store-type=PKCS12',
        "server.ssl.key-store-password=$storePassword", 'server.forward-headers-strategy=none',
        "DB_URL=jdbc:postgresql://127.0.0.1:$databasePort/taskmanager_smoke",
        "DB_USERNAME=$env:POSTGRES_USER", "DB_PASSWORD=$env:POSTGRES_PASSWORD", "JWT_SECRET=$(New-Secret)"
    )
    [IO.File]::WriteAllLines($config, $settings)
    $configUri = ([Uri]$config).AbsoluteUri
    $app = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', ('"' + $jar + '"'),
        ('"--spring.config.location=classpath:/,' + $configUri + '"')) -WorkingDirectory $runDir `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDir 'application.log') `
        -RedirectStandardError (Join-Path $runDir 'application-error.log')

    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($app.HasExited) { throw "Application exited during startup. See $runDir." }
        $status = Invoke-NativeTool 'curl.exe' @('--silent', '--noproxy', '*', '--cacert', $certificate,
            '--max-time', '2', '--output', 'NUL', '--write-out', '%{http_code}', "$baseUrl/actuator/health/readiness") -AllowFailure
        if ($status -eq '200') { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw "Timed out waiting for secure readiness. See $runDir." }

    $health = Invoke-SmokeRequest GET '/actuator/health/readiness' 200
    if ($health.status -ne 'UP' -or $health.PSObject.Properties.Name -contains 'components') { throw 'Unexpected health response.' }
    Invoke-SmokeRequest GET '/api/v1/tasks' 401 | Out-Null
    $credentials = @{ email = "smoke-$runId@example.com"; password = (New-Secret) }
    Invoke-SmokeRequest POST '/api/v1/auth/register' 201 $credentials | Out-Null
    $login = Invoke-SmokeRequest POST '/api/v1/auth/login' 200 $credentials
    if (!$login.accessToken) { throw 'Login did not return an access token.' }
    $token = $login.accessToken
    $task = Invoke-SmokeRequest POST '/api/v1/tasks' 201 @{ title = 'Production smoke task' } $token
    if (!$task.id -or $task.title -ne 'Production smoke task') { throw 'Unexpected task creation response.' }
    $taskPath = '/api/v1/tasks/' + $task.id
    $read = Invoke-SmokeRequest GET $taskPath 200 $null $token
    if ($read.id -ne $task.id) { throw 'Created task could not be read.' }
    $updated = Invoke-SmokeRequest PATCH $taskPath 200 @{ status = 'DONE' } $token
    if ($updated.status -ne 'DONE') { throw 'Task update was not persisted.' }
    Invoke-SmokeRequest GET '/api/v1/tasks' 200 $null $token | Out-Null
    Invoke-SmokeRequest GET '/api/v1/admin/tasks' 403 $null $token | Out-Null
    Invoke-SmokeRequest GET '/v3/api-docs' 403 $null $token | Out-Null
    Invoke-SmokeRequest DELETE $taskPath 204 $null $token
    Invoke-SmokeRequest GET $taskPath 404 $null $token | Out-Null
    Write-Host 'PASS: packaged prod JAR, PostgreSQL migrations, verified TLS, health, registration/login, task CRUD, authorization, and disabled docs.'
} finally {
    if ($app -and !$app.HasExited) { Stop-Process -Id $app.Id; $app.WaitForExit() }
    if ($composeStarted) {
        try { Invoke-NativeTool 'docker' @('compose', '--project-name', $runId, '--file', $composeFile, 'down', '--volumes') }
        catch { Write-Warning "Cleanup failed; remove only Compose project $runId manually." }
    }
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    # Retain logs for diagnosis, removing only this run's generated secret/request files.
    foreach ($name in @('smoke.properties', 'localhost.p12', 'keystore-password.txt', 'request.json', 'response.json', 'authorization.txt')) {
        $secretFile = Join-Path $runDir $name
        if (Test-Path -LiteralPath $secretFile) { Remove-Item -LiteralPath $secretFile -Force }
    }
}
