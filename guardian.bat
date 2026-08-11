@echo off
REM Spring Boot Guardian CLI wrapper script (Windows)
REM Usage: guardian check --project C:\path\to\project [options]
set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%target\spring-boot-guardian-1.0.0-jar-with-dependencies.jar
if not exist "%JAR_PATH%" (
  echo Error: guardian.jar not found. Run "mvn clean package -DskipTests" first.
  exit /b 1
)
java -jar "%JAR_PATH%" %*
