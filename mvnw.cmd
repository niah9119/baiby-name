@REM Maven Wrapper for Windows
@echo off

set JAVA_HOME=%JAVA_HOME:%
if "%JAVA_HOME%"=="" (
    set JAVA=java
) else (
    set JAVA="%JAVA_HOME%\bin\java"
)

set MVN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
set MVN_DIR=%TEMP%\maven-3.9.6

if not exist "%MVN_DIR%" (
    echo Downloading Maven...
    curl -sL "%MVN_URL%" -o %TEMP%\maven.zip
    powershell -Command "Expand-Archive -Path %TEMP%\maven.zip -DestinationPath %TEMP% -Force"
    del %TEMP%\maven.zip
)

"%MVN_DIR%\bin\mvn.bat" %*
