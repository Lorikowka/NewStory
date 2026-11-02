@echo off
setlocal
cd /d "%~dp0.."
if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if not defined JAVA_HOME (
    echo Set JAVA_HOME to your JDK 17 directory first.
    pause
    exit /b 1
)
echo Launching Minecraft 1.20.1 with Story Engine.
echo First launch may download game assets. Keep this window open.
call gradlew.bat runClient --console=plain --stacktrace
set "RUN_RESULT=%ERRORLEVEL%"
echo.
echo Client exit code: %RUN_RESULT%
echo Game log: run\logs\latest.log
pause
exit /b %RUN_RESULT%
