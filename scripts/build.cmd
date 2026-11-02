@echo off
setlocal
cd /d "%~dp0.."
if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if not defined JAVA_HOME (
    echo Set JAVA_HOME to your JDK 17 directory first.
    pause
    exit /b 1
)
echo Building Story Engine. Progress will appear below.
echo The first Minecraft setup can take several minutes.
call gradlew.bat build --console=plain --info --stacktrace
set "BUILD_RESULT=%ERRORLEVEL%"
echo.
echo Build exit code: %BUILD_RESULT%
if "%BUILD_RESULT%"=="0" echo Mod JAR: build\libs
pause
exit /b %BUILD_RESULT%
