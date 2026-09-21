@echo off
title Lion Agent Backend Build
echo Packaging backend...
echo.
cd /d "%~dp0.."

rem Pin JDK 21 (pom.xml java.version=21). Change this path to switch JDK.
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] java not found: %JAVA_HOME%\bin\java.exe
    echo Please install JDK 21 or fix JAVA_HOME in this script.
    pause
    exit /b 1
)
echo Using JAVA_HOME=%JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version

call mvn clean package -DskipTests
if errorlevel 1 (
    echo.
    echo [ERROR] Backend build FAILED.
    pause
    exit /b 1
)
echo.
echo Backend build completed. Output: target/lion-mcp-travel-planner-1.0.0.jar
echo.
pause
