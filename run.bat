@echo off
setlocal
cd /d "%~dp0"
if not exist "build\sp-maacs-simulator.jar" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
    if errorlevel 1 (
        pause
        exit /b 1
    )
)
java -Dfile.encoding=UTF-8 -jar "build\sp-maacs-simulator.jar" %*
if errorlevel 1 pause
