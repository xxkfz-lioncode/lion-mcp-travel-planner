@echo off
title Lion Agent Frontend Build
echo Packaging frontend...
echo.
cd /d "%~dp0..\frontend"
call npm install
call npm run build
echo.
echo Frontend build completed. Output: frontend/dist
echo.
pause
