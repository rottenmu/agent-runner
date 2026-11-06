@echo off
rem ---------------------------------------------------------------------------
rem mvnw.cmd - Windows launcher for Maven (equivalent to the bash script mvnw)
rem
rem Why this file exists: the mvnw script in this repo starts with #!/bin/bash and
rem boots Maven through classworlds, so cmd / PowerShell cannot run it. There is
rem also no official .mvn/wrapper directory. This file is the Windows entry point.
rem
rem NOTE: keep this file pure ASCII. cmd reads .cmd files in the OEM code page,
rem so UTF-8 non-ASCII comments get mis-parsed into bogus commands.
rem
rem Usage (in cmd):
rem   mvnw -pl agent-application -am test
rem   mvnw -pl agent-application -am package
rem
rem Path resolution: environment JAVA_HOME / M2_HOME win; otherwise defaults below.
rem ---------------------------------------------------------------------------
setlocal

if "%JAVA_HOME%"=="" set "JAVA_HOME=D:\.dev\zulu17.56.15-ca-jdk17.0.14-win_x64"
if "%M2_HOME%"=="" set "M2_HOME=D:\.dev\apache-maven-3.6.3"

set "MVNW_CLASSWORLDS=%M2_HOME%\boot\plexus-classworlds-2.6.0.jar"
set "MVNW_CONF=%M2_HOME%\bin\m2.conf"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [mvnw] Invalid JAVA_HOME: %JAVA_HOME%
  echo [mvnw] Set JAVA_HOME to a JDK 17 installation and retry.
  exit /b 1
)
if not exist "%MVNW_CLASSWORLDS%" (
  echo [mvnw] Maven launcher not found: %MVNW_CLASSWORLDS%
  echo [mvnw] Set M2_HOME to a Maven installation and retry.
  exit /b 1
)

rem %~dp0 ends with a backslash; strip it to get the multi-module project root
set "MVNW_PROJECT_DIR=%~dp0"
if "%MVNW_PROJECT_DIR:~-1%"=="\" set "MVNW_PROJECT_DIR=%MVNW_PROJECT_DIR:~0,-1%"

"%JAVA_HOME%\bin\java.exe" -cp "%MVNW_CLASSWORLDS%" "-Dclassworlds.conf=%MVNW_CONF%" "-Dmaven.home=%M2_HOME%" "-Dmaven.multiModuleProjectDirectory=%MVNW_PROJECT_DIR%" org.codehaus.plexus.classworlds.launcher.Launcher %*

exit /b %ERRORLEVEL%
