@echo off
set "SCRIPT_DIR=%~dp0"
set "JAVA_HOME=%SCRIPT_DIR%tools\jdk-21.0.13+9"
set "PATH=%JAVA_HOME%\bin;%SCRIPT_DIR%tools\apache-maven-3.9.6\bin;%PATH%"
"%SCRIPT_DIR%tools\apache-maven-3.9.6\bin\mvn.cmd" %*

