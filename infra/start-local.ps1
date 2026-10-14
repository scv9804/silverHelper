# Run from any directory: powershell -ExecutionPolicy Bypass -File .\infra\start-local.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path .env)) {
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $randomBytes = New-Object byte[] 32
    $rng.GetBytes($randomBytes)
    $dbPassword = [BitConverter]::ToString($randomBytes).Replace('-', '')
    $rng.GetBytes($randomBytes)
    $jwtSecret = [BitConverter]::ToString($randomBytes).Replace('-', '')
    $rng.Dispose()
    @(
        'DB_NAME=silverhelper'
        'DB_USERNAME=silverhelper'
        "DB_PASSWORD=$dbPassword"
        "JWT_SECRET=$jwtSecret"
        'HTTP_PORT=8088'
        'AUTH_CALLBACK_URL=http://localhost:8088/auth/callback'
    ) | Set-Content -Encoding ascii .env
    Write-Host 'infra/.env created with random local DB and JWT secrets.'
}
$portLine = Get-Content .env | Where-Object { $_ -match '^HTTP_PORT=' } | Select-Object -Last 1
$httpPort = if ($portLine) { ($portLine -split '=', 2)[1] } else { '8088' }
if ($httpPort -notmatch '^\d+$') { throw 'HTTP_PORT must be a numeric port.' }
$envPath = Join-Path $PSScriptRoot '.env'
$envLines = [System.IO.File]::ReadAllLines($envPath)
$callbackIndex = -1
for ($index = 0; $index -lt $envLines.Length; $index++) {
    if ($envLines[$index] -match '^AUTH_CALLBACK_URL=') { $callbackIndex = $index }
}
$localhostCallback = "http://localhost:$httpPort/auth/callback"
if ($callbackIndex -lt 0) {
    $envLines += "AUTH_CALLBACK_URL=$localhostCallback"
    [System.IO.File]::WriteAllLines($envPath, $envLines, (New-Object System.Text.UTF8Encoding($false)))
} else {
    $callbackValue = ($envLines[$callbackIndex] -split '=', 2)[1]
    try { $callbackUri = [Uri]$callbackValue } catch { throw 'AUTH_CALLBACK_URL must be a valid URL.' }
    if (-not $callbackUri.IsAbsoluteUri) { throw 'AUTH_CALLBACK_URL must be an absolute URL.' }
    if ($callbackUri.Host -eq 'localhost' -and $callbackUri.Scheme -eq 'http') {
        if ($callbackValue -ne $localhostCallback) {
            $envLines[$callbackIndex] = "AUTH_CALLBACK_URL=$localhostCallback"
            [System.IO.File]::WriteAllLines($envPath, $envLines, (New-Object System.Text.UTF8Encoding($false)))
            Write-Host "Local OAuth callback updated for HTTP_PORT=$httpPort."
        }
    } elseif ($callbackUri.Port -ne [int]$httpPort) {
        throw 'AUTH_CALLBACK_URL port must match HTTP_PORT for local hosting.'
    }
}

docker compose -f docker-compose.prod.yml config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration failed.' }
docker compose -f docker-compose.prod.yml up --build -d
if ($LASTEXITCODE -ne 0) { throw 'Compose startup failed.' }

for ($attempt = 1; $attempt -le 60; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:$httpPort/actuator/health" -TimeoutSec 3
        if ($health.status -eq 'UP') {
            Write-Host "Ready: http://localhost:$httpPort (production profile, HTTP on local network)"
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 3
}
throw 'Backend health did not become UP. Inspect: docker compose -f infra/docker-compose.prod.yml logs --tail=100'
