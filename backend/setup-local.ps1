$ErrorActionPreference = 'Stop'

$localDirectory = Join-Path $PSScriptRoot '.local'
$localConfiguration = Join-Path $localDirectory 'application.properties'

if (Test-Path -LiteralPath $localConfiguration) {
    Write-Host 'Local configuration already exists; keeping the existing signing key.'
    exit 0
}

$keyBytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($keyBytes)
} finally {
    $random.Dispose()
}

[System.IO.Directory]::CreateDirectory($localDirectory) | Out-Null
$contents = "# Local development only. Do not commit this file.`nJWT_SECRET=" +
    [Convert]::ToBase64String($keyBytes) + "`n"
$encoding = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($localConfiguration, $contents, $encoding)

Write-Host 'Local development configuration created. Run .\mvnw.cmd spring-boot:run from backend/.'
