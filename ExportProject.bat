@echo off
:: -----------------------------------------------------------------------------
:: Clean Project Structure Exporter (Pure Batch)
:: Outputs UTF-8 sorted list of project files, skipping .git and other junk.
:: -----------------------------------------------------------------------------

chcp 65001 >nul
setlocal enabledelayedexpansion

:: === CONFIG ===
set "OUTPUT_TXT=project_full_structure.txt"
set "OUTPUT_HTML=project_full_structure.html"

:: === FILTERS (case-insensitive) ===
:: Add /c:"pattern" for each exclusion. Escape backslashes: \\. for literal "\"
set "FILTER=/c:"\\.git\\" /c:"\\.svn\\" /c:"\\.hg\\" /c:"\\.idea\\" /c:"\\.vs\\" ^
        /c:"\\bin\\" /c:"\\obj\\" /c:"\\Debug\\" /c:"\\Release\\" ^
        /c:"node_modules\\" /c:"\\logs\\" /c:"\\temp\\" /c:"\\Backup\\" ^
        /c:".dll" /c:".pdb" /c:".exe" /c:".cache" /c:".log" /c:".nupkg" ^
        /c:".zip" /c:".rar""

:: === TEMP FILES ===
set "TEMPFILE=%TEMP%\project_files.tmp"
if exist "%TEMPFILE%" del "%TEMPFILE%" >nul 2>&1
if exist "%OUTPUT_TXT%" del "%OUTPUT_TXT%" >nul 2>&1
if exist "%OUTPUT_HTML%" del "%OUTPUT_HTML%" >nul 2>&1

set /a count=0
set /a totalSize=0

:: Collect & filter files
for /f "usebackq tokens=*" %%f in (`
    dir /a-d /b /s 2^>nul ^| findstr /i /v %FILTER%
`) do (
    set "fullpath=%%f"
    set "relpath=!fullpath:%CD%\=!"
    set "relpath=!relpath:\=/!"
    for %%A in ("%%f") do set /a fileSize=%%~zA
    set /a totalSize+=!fileSize!
    echo !relpath!>>"%TEMPFILE%"
    set /a count+=1
)

:: Sort and add to TXT with header/footer
sort "%TEMPFILE%" > "%OUTPUT_TXT%"
del "%TEMPFILE%" >nul 2>&1

> "%TEMPFILE%" (
    echo Project Structure - %DATE% %TIME%
    echo ========================================
    echo.
    type "%OUTPUT_TXT%"
    echo.
    echo ========================================
    echo Total files: %count%
    set /a sizeMB=totalSize/1024/1024
    echo Total size : %sizeMB% MB
)
move /y "%TEMPFILE%" "%OUTPUT_TXT%" >nul

:: HTML generation
(
    echo ^<html^>^<head^>^<meta charset="UTF-8"^>
    echo ^<title^>Project Structure^</title^>
    echo ^<style^>body{font-family:monospace;background:#f8f9fa;padding:20px}
    echo .folder{font-weight:bold;color:#2c3e50}.file{color:#34495e}^</style^>
    echo ^</head^>^<body^>
    echo ^<h2^>Project Structure - %DATE% %TIME%^</h2^>
    echo ^<pre^>
) > "%OUTPUT_HTML%"

for /f "usebackq tokens=*" %%L in ("%OUTPUT_TXT%") do (
    set "line=%%L"
    rem Skip header/footer lines in TXT for HTML tree
    echo !line!| findstr /b /c:"Project Structure" /c:"===" /c:"Total files" /c:"Total size" >nul && (
        rem skip
    ) || (
        set "indent="
        set /a depth=0
        for %%C in (!line:/= !) do set /a depth+=1
        for /l %%i in (1,1,!depth!) do set "indent=!indent!    "
        if "!line:~-1!"=="/" (
            echo !indent!^<span class="folder"^>!line!^</span^> >> "%OUTPUT_HTML%"
        ) else (
            echo !indent!^<span class="file"^>!line!^</span^> >> "%OUTPUT_HTML%"
        )
    )
)

(
    echo ^</pre^>
    echo ^<hr^>Total files: %count%^<br^>
    set /a sizeMB=totalSize/1024/1024
    echo Total size : %sizeMB% MB
    echo ^</body^>^</html^>
) >> "%OUTPUT_HTML%"

echo.
echo Clean export complete.
echo TXT  : %CD%\%OUTPUT_TXT%
echo HTML : %CD%\%OUTPUT_HTML%
echo Entries: %count%
set /a sizeMB=totalSize/1024/1024
echo Size : %sizeMB% MB
pause
exit /b
