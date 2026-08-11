. (Join-Path $PSScriptRoot 'common.ps1')
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try { & .\gradlew.bat clean; if ($LASTEXITCODE -ne 0) { throw 'Gradle clean failed' } } finally { Pop-Location }
