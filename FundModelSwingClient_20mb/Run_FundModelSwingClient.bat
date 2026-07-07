@echo off
setlocal
cd /d "%~dp0"
java -jar FundModelSwingClient.jar
if errorlevel 1 (
  echo.
  echo Failed to start FundModelSwingClient.
  echo Make sure Java is installed and available on PATH.
  echo.
  pause
)
