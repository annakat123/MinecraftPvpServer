$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$JdkRoot = Join-Path $ProjectRoot 'runtime\jdk25'
$JavaHome = Get-ChildItem -LiteralPath $JdkRoot -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1 -ExpandProperty FullName
if (-not $JavaHome) { throw 'Java 25 not found. Run setup-local.bat first.' }
$env:JAVA_HOME = $JavaHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
