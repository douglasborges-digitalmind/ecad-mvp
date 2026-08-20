@echo off
setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "MVND_DIST_DIR=%MVNDW_DIST_DIR%"
set "FORWARD_ARGS="
set "CUSTOM_MVND_DIR=false"

:parseArgs
if /I "%~1"=="--mvnd-dir" (
    if "%~2"=="" (
        echo O parametro --mvnd-dir exige o nome da pasta do mvnd.
        exit /b 1
    )
    set "MVND_DIST_DIR=%~2"
    set "CUSTOM_MVND_DIR=true"
    shift
    shift
    goto collectForwardArgs
)

goto resolvedArgs

:collectForwardArgs
if "%~1"=="" goto resolvedArgs
set "FORWARD_ARGS=!FORWARD_ARGS! ^"%~1^""
shift
goto collectForwardArgs

:resolvedArgs
if "%MVND_DIST_DIR%"=="" (
    for /d %%D in ("%SCRIPT_DIR%maven-mvnd-*") do (
        set "MVND_DIST_DIR=%%~nxD"
        goto resolvedDistDir
    )
)

:resolvedDistDir
if "%MVND_DIST_DIR%"=="" (
    echo mvnd wrapper nao encontrou nenhuma pasta maven-mvnd-* em "%SCRIPT_DIR%".
    echo Informe a pasta com --mvnd-dir NOME_DA_PASTA ou pela variavel MVNDW_DIST_DIR.
    exit /b 1
)

set "MVND_CMD=%SCRIPT_DIR%%MVND_DIST_DIR%\bin\mvnd.cmd"

if not exist "%MVND_CMD%" (
    echo mvnd wrapper nao encontrou "%MVND_CMD%".
    exit /b 1
)

if /I "%CUSTOM_MVND_DIR%"=="true" (
    "%MVND_CMD%" !FORWARD_ARGS!
) else (
    "%MVND_CMD%" %*
)
exit /b %ERRORLEVEL%