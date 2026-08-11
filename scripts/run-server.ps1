. (Join-Path $PSScriptRoot 'common.ps1')
$ProjectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'build.ps1')
$Server = Join-Path $ProjectRoot 'local-server'
if (-not (Test-Path (Join-Path $Server 'paper.jar'))) { throw 'Server is not set up. Run setup-local.bat first.' }
Push-Location $Server
try { & java -Xms1G -Xmx2G -jar paper.jar --nogui } finally { Pop-Location }
