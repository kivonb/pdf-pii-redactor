@echo off
setlocal
cd /d "%~dp0"
if not exist target\pdf-pii-redactor-1.0.0.jar (
  ..\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd clean package || exit /b 1
)
..\.tools\java\jdk-21.0.11+10\bin\java.exe -jar target\pdf-pii-redactor-1.0.0.jar
