. (Join-Path $PSScriptRoot 'common.ps1')
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try { & .\gradlew.bat clean build; if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }; New-Item -ItemType Directory -Force -Path 'local-server\plugins' | Out-Null; Copy-Item -Force 'build\libs\PvPBot-1.0.9.jar' 'local-server\plugins\PvPBot.jar' } finally { Pop-Location }
