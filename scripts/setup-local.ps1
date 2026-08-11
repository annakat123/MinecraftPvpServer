param([switch]$AcceptEula)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$hasJava = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'runtime\jdk25') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
if (-not (Test-Path (Join-Path $ProjectRoot 'gradlew.bat')) -or -not $hasJava) { & (Join-Path $PSScriptRoot 'install-tools.ps1') }
. (Join-Path $PSScriptRoot 'common.ps1')
& (Join-Path $PSScriptRoot 'build.ps1')
$Server = Join-Path $ProjectRoot 'local-server'; $Plugins = Join-Path $Server 'plugins'; New-Item -ItemType Directory -Force -Path $Plugins | Out-Null
$paperBuild = 112
$paperName = 'paper-26.2-112.jar'
$expectedPaperHash = 'bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e'
$paperUrl = "https://fill-data.papermc.io/v1/objects/$expectedPaperHash/$paperName"
$paperPath = Join-Path $Server 'paper.jar'
$paperHash = if (Test-Path -LiteralPath $paperPath) { (Get-FileHash -Algorithm SHA256 -LiteralPath $paperPath).Hash.ToLowerInvariant() } else { '' }
if ($paperHash -ne $expectedPaperHash) {
    Invoke-WebRequest -UseBasicParsing -Uri $paperUrl -OutFile $paperPath
    $paperHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $paperPath).Hash.ToLowerInvariant()
}
if ($paperHash -ne $expectedPaperHash) { throw 'Paper SHA-256 mismatch' }
$citizensBuild = 4220
$citizensFile = 'Citizens-2.0.43-b4220.jar'
$citizensUrl = "https://ci.citizensnpcs.co/job/Citizens2/$citizensBuild/artifact/dist/target/$citizensFile"
$citizensPath = Join-Path $Plugins 'Citizens.jar'
if (-not (Test-Path -LiteralPath $citizensPath) -or (Get-Item -LiteralPath $citizensPath).Length -lt 1MB) {
    Invoke-WebRequest -UseBasicParsing -Uri $citizensUrl -OutFile $citizensPath
}
if ((Get-Item -LiteralPath $citizensPath).Length -lt 1MB) { throw 'Citizens download is unexpectedly small' }
@"
online-mode=true
server-ip=
server-port=25565
motd=PvP Bot Lab - local Sword practice
gamemode=adventure
difficulty=normal
spawn-protection=0
view-distance=8
simulation-distance=6
max-players=10
enable-command-block=false
"@ | Set-Content -Encoding ascii -LiteralPath (Join-Path $Server 'server.properties')
if (-not $AcceptEula) { $answer = Read-Host 'Mojang EULA (https://aka.ms/MinecraftEULA) must be accepted. Type YES to accept'; if ($answer -ne 'YES') { throw 'EULA was not accepted' } }
'eula=true' | Set-Content -Encoding ascii -LiteralPath (Join-Path $Server 'eula.txt')
"PaperBuild=$paperBuild`nPaperFile=$paperName`nPaperSHA256=$paperHash`nCitizensBuild=$citizensBuild`nCitizensFile=$citizensFile" | Set-Content -Encoding ascii -LiteralPath (Join-Path $Server 'resolved-versions.txt')
Write-Host "Local server ready. Paper build $paperBuild, Citizens build $citizensBuild."
