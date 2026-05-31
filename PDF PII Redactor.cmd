@echo off
setlocal
cd /d "%~dp0"

if not exist target\pdf-pii-redactor-1.0.0.jar (
  echo Building PDF PII Redactor...
  ..\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd clean package
  if errorlevel 1 (
    echo.
    echo Build failed. Press any key to close.
    pause > nul
    exit /b 1
  )
)

start "PDF PII Redactor" ..\.tools\java\jdk-21.0.11+10\bin\javaw.exe -jar target\pdf-pii-redactor-1.0.0.jar
