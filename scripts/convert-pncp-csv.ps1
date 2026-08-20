<#
.SYNOPSIS
    Converte unidadeMunicipio.csv para o formato esperado pela API setup-pncp-urls,
    cruzando com prefeituras_organizada.xlsx para obter CNPJs reais.

.DESCRIPTION
    O endpoint /api/fontes/setup-pncp-urls exige um CSV com cabecalho:
        CNPJ;MUNICIPIO;UF;UNIDADEECAD

    A planilha prefeituras_organizada.xlsx tem 27 abas (Unidade AC, Unidade AL, ...),
    cada uma com municipios de uma UF e seus CNPJs.

    Este script:
    1. Le TODAS as abas da planilha, extrai UF do nome da aba
    2. Constroi indice: UF + Prefeitura (normalizado) -> CNPJ
    3. Le o CSV unidadeMunicipio.csv original
    4. Cruza por UF + nome do municipio (normalizado, sem acentos)
    5. Gera pncp_input.csv com CNPJs reais onde encontrados
    6. Registros sem CNPJ sao ignorados com aviso

.PARAMETER InputCsv
    Caminho do CSV original (padrao: unidadeMunicipio.csv)

.PARAMETER ExcelPath
    Caminho da planilha de prefeituras com CNPJs (padrao: prefeituras_organizada.xlsx)

.PARAMETER OutputCsv
    Caminho do CSV de saida (padrao: pncp_input.csv)

.PARAMETER Limit
    Limite de registros para teste (0 = todos). Ex: -Limit 5

.EXAMPLE
    .\scripts\convert-pncp-csv.ps1
    # Cruzamento completo com prefeituras_organizada.xlsx

.EXAMPLE
    .\scripts\convert-pncp-csv.ps1 -Limit 5
    # Apenas 5 registros para teste rapido

.NOTES
    Referencia: docs/DOCKER_EXECUCAO_LOCAL.md (fluxo setup-pncp / import).
    Formato da planilha: multiplas abas "Unidade {UF}" com colunas:
    UF, Prefeitura, Unidade, PNCP, Site, Instagram, Outros canais, CNPJs, FEITO
#>
param(
    [string]$InputCsv = "unidadeMunicipio.csv",
    [string]$ExcelPath = "prefeituras_organizada.xlsx",
    [string]$OutputCsv = "pncp_input.csv",
    [int]$Limit = 0,
    [switch]$AutoInstall
)

$ErrorActionPreference = "Stop"

# ============================================================
# Funcao: normaliza nome (remove acentos, uppercase, trim)
# ============================================================
function Normalize-Name {
    param([string]$Name)
    if ([string]::IsNullOrWhiteSpace($Name)) { return "" }
    $normalized = $Name.Trim().ToUpperInvariant().Normalize([System.Text.NormalizationForm]::FormD)
    $sb = [System.Text.StringBuilder]::new()
    foreach ($ch in $normalized.ToCharArray()) {
        if ([System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch) -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$sb.Append($ch)
        }
    }
    return $sb.ToString().Trim()
}

# ============================================================
# ETAPA 1: Carregar CNPJs de TODAS as abas da planilha Excel
# ============================================================
Write-Host "=== Carregando CNPJs de $ExcelPath ===" -ForegroundColor Cyan

if (-not (Test-Path $ExcelPath)) {
    Write-Error "Planilha nao encontrada: $ExcelPath"
    exit 1
}

if (-not (Get-Module -ListAvailable -Name ImportExcel)) {
    if (-not $AutoInstall) {
        Write-Host "O modulo ImportExcel nao esta instalado." -ForegroundColor Yellow
        Write-Host "Para instalar automaticamente, re-execute com -AutoInstall:" -ForegroundColor Yellow
        Write-Host "  .\scripts\convert-pncp-csv.ps1 -AutoInstall" -ForegroundColor White
        Write-Host "Ou instale manualmente: Install-Module -Name ImportExcel -Scope CurrentUser -Force -AllowClobber" -ForegroundColor Gray
        exit 1
    }
    Write-Host "Instalando modulo ImportExcel (-AutoInstall)..." -ForegroundColor Yellow
    try {
        Install-Module -Name ImportExcel -Scope CurrentUser -Force -AllowClobber -ErrorAction Stop
    } catch {
        Write-Error "Falha ao instalar ImportExcel: $($_.Exception.Message). Execute manualmente: Install-Module -Name ImportExcel -Scope CurrentUser -Force -AllowClobber"
        exit 1
    }
}
try {
    Import-Module ImportExcel -Force -ErrorAction Stop
} catch {
    Write-Error "Falha ao importar ImportExcel: $($_.Exception.Message)"
    exit 1
}

# Obtem todas as abas
$sheets = Get-ExcelSheetInfo $ExcelPath
Write-Host "  $($sheets.Count) abas encontradas" -ForegroundColor Gray

$cnpjMap = @{}
$unidadeMap = @{}     # UF+Prefeitura -> Unidade
$statsFromSheet = 0
$statsInvalidCnpj = 0
$statsNoCnpj = 0

foreach ($sheet in $sheets) {
    $sheetName = $sheet.Name
    # Extrai UF do nome da aba: "Unidade AC" -> "AC", "Unidade SP" -> "SP"
    $ufMatch = [regex]::Match($sheetName, 'Unidade\s+(\w{2})')
    if (-not $ufMatch.Success) {
        Write-Host "  [IGNORADO] Nome da aba nao tem UF: $sheetName" -ForegroundColor DarkGray
        continue
    }
    $ufFromSheet = $ufMatch.Groups[1].Value.ToUpperInvariant()

    $rows = Import-Excel -Path $ExcelPath -WorksheetName $sheetName
    $validCount = 0
    foreach ($row in $rows) {
        $municipio = Normalize-Name -Name $row.Prefeitura
        if ([string]::IsNullOrWhiteSpace($municipio)) { continue }

        $cnpjRaw = if ($row.CNPJs) { $row.CNPJs.ToString().Trim() } else { "" }
        $cnpj = ($cnpjRaw -replace '[^\d]', '')

        $key = "$ufFromSheet|$municipio"
        $validCount++

        if ($cnpj.Length -eq 14) {
            $cnpjMap[$key] = $cnpj
        } elseif ($cnpj.Length -gt 0) {
            Write-Verbose "  CNPJ invalido: $ufFromSheet | $municipio -> '$cnpjRaw' ($($cnpj.Length)d)"
            $statsInvalidCnpj++
        } else {
            $statsNoCnpj++
        }

        # Guarda unidade para usar como fallback
        if ($row.Unidade -and -not $unidadeMap.ContainsKey($key)) {
            $unidadeMap[$key] = $row.Unidade.ToString().Trim()
        }
    }
    $statsFromSheet += $validCount
}

Write-Host "  Indice construido: $($cnpjMap.Count) prefeituras com CNPJ" -ForegroundColor Green
Write-Host "  Total lido da planilha: $statsFromSheet | CNPJ invalido: $statsInvalidCnpj | Sem CNPJ: $statsNoCnpj" -ForegroundColor Gray

# ============================================================
# ETAPA 2: Processar CSV e cruzar com CNPJs
# ============================================================
Write-Host ""
Write-Host "=== Processando $InputCsv ===" -ForegroundColor Cyan

if (-not (Test-Path $InputCsv)) {
    Write-Error "Arquivo nao encontrado: $InputCsv"
    exit 1
}

$lines = Get-Content $InputCsv -Encoding UTF8
if ($lines.Count -lt 2) {
    Write-Error "CSV precisa ter pelo menos cabecalho + 1 linha de dados."
    exit 1
}

# Detecta delimitador
$headerLine = $lines[0]
$delimiter = if ($headerLine.Contains(";")) { ";" } else { "," }
Write-Host "  Delimitador: '$delimiter'" -ForegroundColor Gray

# Parse cabecalho
$headers = $headerLine.Split($delimiter) | ForEach-Object { $_.Trim().Trim('"').ToUpperInvariant() }
Write-Host "  Colunas: $($headers -join ' | ')" -ForegroundColor Gray

# Encontra indices
$ufIdx = [Array]::IndexOf($headers, "UF")
$municipioIdx = -1
for ($i = 0; $i -lt $headers.Count; $i++) {
    if ($headers[$i] -like "MUNIC*") {
        $municipioIdx = $i
        break
    }
}
# Fallback: procura por coluna contendo "MUNIC" ou "CIDADE"
if ($municipioIdx -lt 0) {
    for ($i = 0; $i -lt $headers.Count; $i++) {
        if ($headers[$i] -match "MUNIC|CIDADE") {
            $municipioIdx = $i
            break
        }
    }
}
$unidadeIdx = [Array]::IndexOf($headers, "UNIDADE")

if ($ufIdx -lt 0 -or $municipioIdx -lt 0) {
    Write-Error "Colunas obrigatorias 'UF' e 'MUNICIPIO' nao encontradas. Headers: $($headers -join ', ')"
    exit 1
}

Write-Host "  Indices: UF=$ufIdx, MUNICIPIO=$municipioIdx, UNIDADE=$unidadeIdx" -ForegroundColor Gray

# ============================================================
# ETAPA 3: Gerar CSV de saida
# ============================================================
$outputLines = [System.Collections.ArrayList]::new()
[void]$outputLines.Add("CNPJ;MUNICIPIO;UF;UNIDADEECAD")

$dataLines = $lines[1..($lines.Count - 1)]
$total = $dataLines.Count
$matched = 0
$notFound = 0
$skipped = 0
$processed = 0

# Lista para relatorio de nao encontrados
$notFoundList = [System.Collections.ArrayList]::new()

foreach ($line in $dataLines) {
    if ([string]::IsNullOrWhiteSpace($line)) { $skipped++; continue }

    $fields = $line.Split($delimiter) | ForEach-Object { $_.Trim().Trim('"') }

    $uf = if ($ufIdx -lt $fields.Count) { $fields[$ufIdx].Trim().ToUpperInvariant() } else { "" }
    $municipioRaw = if ($municipioIdx -lt $fields.Count) { $fields[$municipioIdx] } else { "" }
    $unidade = if ($unidadeIdx -ge 0 -and $unidadeIdx -lt $fields.Count) { $fields[$unidadeIdx].Trim() } else { "" }

    if ([string]::IsNullOrWhiteSpace($uf) -or [string]::IsNullOrWhiteSpace($municipioRaw)) {
        $skipped++
        continue
    }

    $municipioNorm = Normalize-Name -Name $municipioRaw
    $key = "$uf|$municipioNorm"

    # Busca CNPJ
    $cnpj = if ($cnpjMap.ContainsKey($key)) { $cnpjMap[$key] } else { "" }

    if ($cnpj -eq "") {
        # Tenta busca parcial (municipio do CSV contem nome da planilha ou vice-versa)
        foreach ($mapKey in $cnpjMap.Keys) {
            $parts = $mapKey -split '\|', 2
            if ($parts[0] -eq $uf) {
                $mapMunicipio = $parts[1]
                # Comparacao parcial: ex: "SAO PAULO" contido em "SAO PAULO - SP" ou vice-versa
                if (($municipioNorm.Contains($mapMunicipio) -or $mapMunicipio.Contains($municipioNorm)) -and $mapMunicipio.Length -gt 2) {
                    $cnpj = $cnpjMap[$mapKey]
                    break
                }
            }
        }
    }

    if ($cnpj -eq "") {
        $notFound++
        [void]$notFoundList.Add("$uf|$municipioRaw")
        # Registros sem CNPJ sao ignorados (nao entram no CSV de saida)
        continue
    }

    $matched++
    $processed++

    # Busca unidade da planilha como fallback
    $unidadeEcad = $unidade
    if ([string]::IsNullOrWhiteSpace($unidadeEcad) -and $unidadeMap.ContainsKey($key)) {
        $unidadeEcad = $unidadeMap[$key]
    }

    # Escapa campos com ;
    $munEscaped = if ($municipioRaw.Contains(";")) { "`"$municipioRaw`"" } else { $municipioRaw }
    $unidEscaped = if ($unidadeEcad.Contains(";")) { "`"$unidadeEcad`"" } else { $unidadeEcad }

    [void]$outputLines.Add("$cnpj;$munEscaped;$uf;$unidEscaped")

    if ($Limit -gt 0 -and $processed -ge $Limit) {
        Write-Host "  [LIMITE] Parando em $Limit registros" -ForegroundColor Yellow
        break
    }
}

# ============================================================
# ETAPA 4: Escrever arquivo de saida
# ============================================================
$outputLines -join "`n" | Out-File -FilePath $OutputCsv -Encoding UTF8 -NoNewline

# ============================================================
# Relatorio
# ============================================================
Write-Host ""
Write-Host "=== Conversao concluida ===" -ForegroundColor Green
Write-Host "  Entrada: $InputCsv ($total linhas)" -ForegroundColor Gray
Write-Host "  Saida:   $OutputCsv ($processed registros com CNPJ)" -ForegroundColor Cyan
Write-Host "  Matched: $matched | Sem CNPJ: $notFound | Pulados: $skipped" -ForegroundColor Gray

if ($notFound -gt 0) {
    Write-Host ""
    Write-Host "  ${notFound} municipios NAO encontrados na planilha:" -ForegroundColor Yellow
    # Mostra primeiros 30
    $notFoundList | Select-Object -First 30 | ForEach-Object {
        Write-Host "    $_" -ForegroundColor Yellow
    }
    if ($notFoundList.Count -gt 30) {
        Write-Host "    ... e mais $($notFoundList.Count - 30)" -ForegroundColor DarkGray
        # Salva lista completa
        $notFoundList | Out-File -FilePath "pncp_not_found.txt" -Encoding UTF8
        Write-Host "    Lista completa salva em: pncp_not_found.txt" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "[OBSOLETO] Este script nao e mais necessario." -ForegroundColor Yellow
Write-Host "  O arquivo fontesPNCP.csv ja contem CNPJs corretos." -ForegroundColor Yellow
Write-Host "  As fontes sao criadas automaticamente no startup via municipiosPNCP.json." -ForegroundColor Yellow
Write-Host "  Use: .\scripts\ecadexecute.ps1 -Action run    (subir solucao)" -ForegroundColor Cyan
Write-Host "  Use: .\scripts\ecadexecute.ps1 -Action status (verificar estado)" -ForegroundColor Cyan