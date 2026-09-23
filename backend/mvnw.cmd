@ECHO OFF
SETLOCAL
SET "PROJECT_DIR=%~dp0"
SET "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
SET "WRAPPER_JAR=%PROJECT_DIR%\.mvn\wrapper\maven-wrapper.jar"
IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Downloading Apache Maven Wrapper 3.3.4...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar' -OutFile '%WRAPPER_JAR%'"
  IF ERRORLEVEL 1 EXIT /B 1
)
IF DEFINED JAVA_HOME (
  SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
  SET "JAVA_EXE=java.exe"
)
"%JAVA_EXE%" "-Dmaven.multiModuleProjectDirectory=%PROJECT_DIR%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
EXIT /B %ERRORLEVEL%
