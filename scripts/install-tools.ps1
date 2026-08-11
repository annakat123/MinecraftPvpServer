$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Downloads = Join-Path $ProjectRoot 'runtime\downloads'
New-Item -ItemType Directory -Force -Path $Downloads | Out-Null
function Download-Checked([string]$Url,[string]$ChecksumUrl,[string]$Output) {
    $expected = (curl.exe -L --fail -s $ChecksumUrl).Trim().Split(' ')[0].ToLowerInvariant()
    if (-not (Test-Path $Output)) { curl.exe -L --fail --retry 3 -o $Output $Url } else { curl.exe -L --fail --retry 3 -C - -o $Output $Url }
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Output).Hash.ToLowerInvariant()
    if ($expected -ne $actual) { throw "Checksum mismatch: $Output" }
}
$JdkZip = Join-Path $Downloads 'corretto25.zip'
Download-Checked 'https://corretto.aws/downloads/latest/amazon-corretto-25-x64-windows-jdk.zip' 'https://corretto.aws/downloads/latest_sha256/amazon-corretto-25-x64-windows-jdk.zip' $JdkZip
$JdkRoot = Join-Path $ProjectRoot 'runtime\jdk25'
if (-not (Test-Path $JdkRoot)) { New-Item -ItemType Directory -Path $JdkRoot | Out-Null; Expand-Archive -LiteralPath $JdkZip -DestinationPath $JdkRoot }
$JavaHome = Get-ChildItem -LiteralPath $JdkRoot -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME = $JavaHome
if (-not (Test-Path (Join-Path $ProjectRoot 'gradlew.bat'))) { throw 'Committed Gradle Wrapper files are missing' }
& (Join-Path $JavaHome 'bin\java.exe') -version
