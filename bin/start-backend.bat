@echo off
rem ============================================================
rem Generic Java / Spring Boot backend starter (Windows)
rem NOTE: keep this file pure ASCII. cmd.exe parses .bat as GBK,
rem       non-ASCII text breaks parsing -> garbled output + window closes.
rem
rem Usage:
rem   start-backend.bat              default profile(dev), port from project config
rem   start-backend.bat prod         specify profile
rem   start-backend.bat prod 8081    specify profile + port
rem
rem Reuse in another project: only edit the CONFIG block below.
rem ============================================================

rem ===== CONFIG (change these lines for another project) =====
set "APP_NAME=Lion Agent"
rem Fixed JDK path; leave empty to fall back to JAVA_HOME / PATH
set "DEFAULT_JDK=C:\Program Files\Java\jdk-21"
rem Default spring profile (overridable by first argument)
set "DEFAULT_PROFILE=dev"
rem Default JVM options; external env JAVA_OPTS takes precedence
set "DEFAULT_JAVA_OPTS=-Xms512m -Xmx1024m -Dfile.encoding=UTF-8"
rem Jar path relative to project root; leave empty to auto-detect newest jar in target\
set "JAR_FILE="
rem Maven goals used when the jar is missing
set "BUILD_GOALS=-B clean package -DskipTests"
rem ============================================================

title %APP_NAME% Backend
echo ============================================================
echo  %APP_NAME% Backend Starter
echo  Usage: start-backend.bat [profile] [port]
echo ============================================================
echo.

rem Project root = parent directory of bin
cd /d "%~dp0.."
set "ROOT=%CD%"

rem ---------- 1. Pick JDK ----------
rem Order: JDK_HOME > DEFAULT_JDK > JAVA_HOME > java in PATH
set "JAVA_BIN="
if defined JDK_HOME if exist "%JDK_HOME%\bin\java.exe" set "JAVA_BIN=%JDK_HOME%\bin\java.exe"
if not defined JAVA_BIN if defined DEFAULT_JDK if exist "%DEFAULT_JDK%\bin\java.exe" set "JAVA_BIN=%DEFAULT_JDK%\bin\java.exe"
if not defined JAVA_BIN if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_BIN for /f "delims=" %%P in ('where java 2^>nul') do if not defined JAVA_BIN set "JAVA_BIN=%%P"
if not defined JAVA_BIN (
    echo [ERROR] java not found. Set JDK_HOME, or fix DEFAULT_JDK in this script.
    pause
    exit /b 1
)

rem ---------- 2. Profile and port ----------
set "PROFILE=%~1"
if not defined PROFILE set "PROFILE=%DEFAULT_PROFILE%"
set "PORT=%~2"
if defined PORT (set "PORT_ARG=--server.port=%PORT%") else (set "PORT_ARG=")

rem ---------- 3. Locate jar ----------
set "JAR="
if defined JAR_FILE set "JAR=%ROOT%\%JAR_FILE%"
if not defined JAR (
    for /f "delims=" %%J in ('dir /b /a-d /o-d "target\*.jar" 2^>nul') do (
        if not defined JAR if /i not "%%~xJ"==".original" set "JAR=%ROOT%\target\%%J"
    )
)

rem ---------- 4. Build if jar is missing ----------
if not defined JAR (
    echo [INFO] jar not found, building now...
    set "MVN=mvn"
    if exist "%ROOT%\mvnw.cmd" set "MVN=%ROOT%\mvnw.cmd"
    call %MVN% %BUILD_GOALS%
    if errorlevel 1 (
        echo.
        echo [ERROR] Build failed, cannot start backend.
        pause
        exit /b 1
    )
    if defined JAR_FILE (
        set "JAR=%ROOT%\%JAR_FILE%"
    ) else (
        for /f "delims=" %%J in ('dir /b /a-d /o-d "target\*.jar" 2^>nul') do (
            if not defined JAR if /i not "%%~xJ"==".original" set "JAR=%ROOT%\target\%%J"
        )
    )
)
if not defined JAR (
    echo [ERROR] No jar found under target\. Build the project first.
    pause
    exit /b 1
)

rem ---------- 5. Run ----------
if defined JAVA_OPTS (set "JVM_OPTS=%JAVA_OPTS%") else (set "JVM_OPTS=%DEFAULT_JAVA_OPTS%")

echo Using java : %JAVA_BIN%
echo Profile    : %PROFILE%
if defined PORT (echo Port       : %PORT%) else (echo Port       : project default)
echo Jar        : %JAR%
if defined PORT (echo URL        : http://localhost:%PORT%) else (echo URL        : see server.port in project config)
echo Press Ctrl+C to stop. Closing this window also stops it.
echo.

"%JAVA_BIN%" %JVM_OPTS% -Dspring.profiles.active=%PROFILE% -jar "%JAR%" %PORT_ARG%

echo.
echo Backend stopped.
pause
