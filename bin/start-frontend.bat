@echo off
title Lion Agent Frontend
echo Starting frontend dev server...
echo URL: http://localhost:5173
echo Press Ctrl+C to stop. Closing this window also stops it.
echo.
cd /d "%~dp0..\frontend"
call npm run dev
echo.
echo Frontend stopped.
pause
