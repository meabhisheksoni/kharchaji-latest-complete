@echo off
setlocal EnableExtensions EnableDelayedExpansion

:: ---------------------------------------------------
:: Configure UTF-8 encoding for Unicode/Rich support
:: ---------------------------------------------------
set "PYTHONIOENCODING=utf-8"
chcp 65001 >nul 2>&1

:: ---------------------------------------------------
:: Determine Repository Root dynamically via %~dp0
:: ---------------------------------------------------
set "REPO_ROOT=%~dp0"
if "%REPO_ROOT:~-1%"=="\" set "REPO_ROOT=%REPO_ROOT:~0,-1%"
cd /d "%REPO_ROOT%"

:REDETECT
:: ---------------------------------------------------
:: CBM Discovery
:: ---------------------------------------------------
set "CBM_EXE="
set "CBM_STATUS=NOT FOUND"

if exist "%LOCALAPPDATA%\Programs\codebase-memory-mcp\codebase-memory-mcp.exe" (
    set "CBM_EXE=%LOCALAPPDATA%\Programs\codebase-memory-mcp\codebase-memory-mcp.exe"
    set "CBM_STATUS=FOUND"
) else (
    for /f "tokens=*" %%i in ('where.exe codebase-memory-mcp.exe 2^>nul') do (
        if not defined CBM_EXE (
            set "CBM_EXE=%%i"
            set "CBM_STATUS=FOUND"
        )
    )
    if not defined CBM_EXE (
        for /f "tokens=*" %%i in ('where.exe codebase-memory-mcp 2^>nul') do (
            if not defined CBM_EXE (
                set "CBM_EXE=%%i"
                set "CBM_STATUS=FOUND"
            )
        )
    )
)

:: ---------------------------------------------------
:: CRG Discovery
:: ---------------------------------------------------
set "CRG_EXE="
set "CRG_STATUS=NOT FOUND"

for /f "tokens=*" %%i in ('where.exe code-review-graph.exe 2^>nul') do (
    if not defined CRG_EXE (
        set "CRG_EXE=%%i"
        set "CRG_STATUS=FOUND"
    )
)
if not defined CRG_EXE (
    for /f "tokens=*" %%i in ('where.exe code-review-graph 2^>nul') do (
        if not defined CRG_EXE (
            set "CRG_EXE=%%i"
            set "CRG_STATUS=FOUND"
        )
    )
)

:MENU
cls
echo ===================================================
echo        CBM + CRG GRAPH SYNC MANAGER
echo ===================================================
echo.
echo Project : %REPO_ROOT%
echo.
echo CBM : %CBM_STATUS%
echo CRG : %CRG_STATUS%
echo.
echo [1] Quick Sync Both Graphs (Incremental)
echo [2] Check Status of Both Graphs
echo [3] Full Rebuild Both Graphs
echo [4] Sync CBM Only
echo [5] Sync CRG Only
echo [6] Launch CBM Visualizer
echo [7] Launch CRG Interactive Graph
echo [8] Exit
echo.
set CHOICE=
set /p CHOICE=Select an option [1-8]: 
if defined CHOICE set "CHOICE=!CHOICE: =!"

if "!CHOICE!"=="1" goto QUICK_SYNC
if "!CHOICE!"=="2" goto CHECK_STATUS
if "!CHOICE!"=="3" goto FULL_REBUILD
if "!CHOICE!"=="4" goto SYNC_CBM
if "!CHOICE!"=="5" goto SYNC_CRG
if "!CHOICE!"=="6" goto LAUNCH_CBM_UI
if "!CHOICE!"=="7" goto LAUNCH_CRG_UI
if "!CHOICE!"=="8" goto EXIT

echo.
echo [WARN] Invalid choice. Please enter a number between 1 and 8.
echo.
pause
goto MENU

:QUICK_SYNC
echo.
echo ===================================================
echo   QUICK SYNC (INCREMENTAL)
echo ===================================================
echo.
echo [1/2] Updating CBM AST Knowledge Graph...
if defined CBM_EXE (
    "%CBM_EXE%" cli --progress index_repository --repo-path "%REPO_ROOT%"
    if !ERRORLEVEL! equ 0 (
        echo [OK] CBM index complete.
    ) else (
        echo [ERROR] CBM sync failed with code !ERRORLEVEL!.
    )
) else (
    echo [SKIP] CBM not found. Skipping CBM sync.
)
echo.
echo [2/2] Updating CRG Blast Radius Graph...
if defined CRG_EXE (
    "%CRG_EXE%" update --brief --repo "%REPO_ROOT%"
    if !ERRORLEVEL! equ 0 (
        echo [OK] CRG update complete.
    ) else (
        echo [ERROR] CRG update failed with code !ERRORLEVEL!.
    )
) else (
    echo [SKIP] CRG not found. Skipping CRG sync.
)
echo.
echo SYNC COMPLETE
echo.
pause
goto MENU

:CHECK_STATUS
echo.
echo ===================================================
echo   GRAPH STATUS
echo ===================================================
echo.
echo --- CBM Status ---
if defined CBM_EXE (
    "%CBM_EXE%" cli list_projects
) else (
    echo [SKIP] CBM not found.
)
echo.
echo --- CRG Status ---
if defined CRG_EXE (
    "%CRG_EXE%" status --repo "%REPO_ROOT%"
) else (
    echo [SKIP] CRG not found.
)
echo.
pause
goto MENU

:FULL_REBUILD
echo.
echo ===================================================
echo   FULL GRAPH REBUILD
echo ===================================================
echo.
echo WARNING:
echo This performs a full graph rebuild.
echo.
set CONFIRM=
set /p CONFIRM=Proceed? (Y/N): 
if defined CONFIRM set "CONFIRM=!CONFIRM: =!"
if /i not "!CONFIRM!"=="Y" (
    echo.
    echo [SKIP] Rebuild aborted by user.
    echo.
    pause
    goto MENU
)
echo.
echo [1/2] Rebuilding CBM AST Knowledge Graph...
if defined CBM_EXE (
    "%CBM_EXE%" cli --progress index_repository --repo-path "%REPO_ROOT%" --mode full
    if !ERRORLEVEL! equ 0 (
        echo [OK] CBM full rebuild complete.
    ) else (
        echo [ERROR] CBM rebuild failed with code !ERRORLEVEL!.
    )
) else (
    echo [SKIP] CBM not found. Skipping CBM rebuild.
)
echo.
echo [2/2] Rebuilding CRG Blast Radius Graph...
if defined CRG_EXE (
    "%CRG_EXE%" build --repo "%REPO_ROOT%"
    if !ERRORLEVEL! equ 0 (
        echo [OK] CRG build complete.
    ) else (
        echo [ERROR] CRG build failed with code !ERRORLEVEL!.
    )
) else (
    echo [SKIP] CRG not found. Skipping CRG build.
)
echo.
echo REBUILD COMPLETE
echo.
pause
goto MENU

:SYNC_CBM
echo.
echo ===================================================
echo   SYNC CBM ONLY
echo ===================================================
echo.
if defined CBM_EXE (
    echo Updating CBM AST Knowledge Graph...
    "%CBM_EXE%" cli --progress index_repository --repo-path "%REPO_ROOT%"
    if !ERRORLEVEL! equ 0 (
        echo [OK] CBM sync complete.
    ) else (
        echo [ERROR] CBM sync failed with code !ERRORLEVEL!.
    )
) else (
    echo [ERROR] CBM executable not found.
)
echo.
pause
goto MENU

:SYNC_CRG
echo.
echo ===================================================
echo   SYNC CRG ONLY
echo ===================================================
echo.
if defined CRG_EXE (
    echo Updating CRG Blast Radius Graph...
    "%CRG_EXE%" update --brief --repo "%REPO_ROOT%"
    if !ERRORLEVEL! equ 0 (
        echo [OK] CRG sync complete.
    ) else (
        echo [ERROR] CRG sync failed with code !ERRORLEVEL!.
    )
) else (
    echo [ERROR] CRG executable not found.
)
echo.
pause
goto MENU

:LAUNCH_CBM_UI
echo.
echo ===================================================
echo   LAUNCH CBM VISUALIZER
echo ===================================================
echo.
if defined CBM_EXE (
    echo Starting CBM UI on port 9749...
    start "" "%CBM_EXE%" --ui=true --port=9749
    echo Opening browser at http://localhost:9749 ...
    start http://localhost:9749 2>nul
    echo [OK] CBM UI launch initiated.
) else (
    echo [ERROR] CBM executable not found.
)
echo.
pause
goto MENU

:LAUNCH_CRG_UI
echo.
echo ===================================================
echo   LAUNCH CRG INTERACTIVE GRAPH
echo ===================================================
echo.
if defined CRG_EXE (
    echo Generating CRG interactive visualization...
    "%CRG_EXE%" visualize --repo "%REPO_ROOT%"
    if !ERRORLEVEL! equ 0 (
        if exist "%REPO_ROOT%\.code-review-graph\graph.html" (
            echo Opening %REPO_ROOT%\.code-review-graph\graph.html ...
            start "" "%REPO_ROOT%\.code-review-graph\graph.html"
            echo [OK] CRG interactive graph opened.
        ) else (
            echo [WARN] graph.html was not found in .code-review-graph directory.
        )
    ) else (
        echo [ERROR] CRG visualize failed with code !ERRORLEVEL!.
    )
) else (
    echo [ERROR] CRG executable not found.
)
echo.
pause
goto MENU

:EXIT
echo.
echo Exiting Graph Sync Manager.
exit /b 0
