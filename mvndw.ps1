[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$MvndDir = $env:MVNDW_DIST_DIR,
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Test-JavaHome([string]$Path) {
    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path (Join-Path $Path "bin/java.exe"))
}

if (-not (Test-JavaHome $env:JAVA_HOME)) {
    $javaCommand = Get-Command javac -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    }

    if ($null -ne $javaCommand -and -not [string]::IsNullOrWhiteSpace($javaCommand.Source)) {
        $javaBin = Split-Path -Parent $javaCommand.Source
        if (-not [string]::IsNullOrWhiteSpace($javaBin)) {
            $javaHome = Split-Path -Parent $javaBin
            if (Test-JavaHome $javaHome) {
                $env:JAVA_HOME = $javaHome
            }
        }
    }

    if (-not (Test-JavaHome $env:JAVA_HOME)) {
        $javaSettings = & java -XshowSettings:properties -version 2>&1
        $javaHomeLine = $javaSettings | Where-Object { $_ -match '^\s*java\.home\s*=\s*(.+?)\s*$' } | Select-Object -First 1
        if ($javaHomeLine -match '^\s*java\.home\s*=\s*(.+?)\s*$') {
            $javaHome = $Matches[1].Trim()
            if (Test-JavaHome $javaHome) {
                $env:JAVA_HOME = $javaHome
            }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($MvndDir)) {
    $candidate = Get-ChildItem -Path $scriptDir -Directory -Filter 'maven-mvnd-*' |
        Sort-Object Name |
        Select-Object -First 1

    if ($null -ne $candidate) {
        $MvndDir = $candidate.Name
    }
}

if ([string]::IsNullOrWhiteSpace($MvndDir)) {
    Write-Error "mvnd wrapper nao encontrou nenhuma pasta maven-mvnd-* em '$scriptDir'. Informe -MvndDir ou MVNDW_DIST_DIR."
    exit 1
}

$mvnd = Join-Path $scriptDir "$MvndDir/bin/mvnd.cmd"

if (-not (Test-Path $mvnd)) {
    Write-Error "mvnd wrapper nao encontrou '$mvnd'."
    exit 1
}

& $mvnd @Arguments
exit $LASTEXITCODE