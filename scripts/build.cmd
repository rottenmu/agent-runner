@echo off
REM ============================================================
REM 智能体运行平台 - 统一构建脚本 (Windows CMD)
REM 使用 clean install 避免引用本地仓库旧模块 jar（P1-5）
REM 用法: scripts\build.cmd [install|check]
REM ============================================================
set ROOT=%~dp0..
set PORT=9900
cd /d %ROOT%

if "%1"=="check" goto check
if "%1"=="start" goto start

echo ==^> clean install admin-shell（含全部依赖模块）
call mvnw.cmd clean install -pl admin-shell -am -Dmaven.test.skip=true -DskipTests -q
if errorlevel 1 exit /b 1
echo ==^> 构建产物: %ROOT%\admin-shell\target\admin-shell-1.0.0.jar
goto :eof

:start
echo ==^> 启动后端 :%PORT%
%JAVA_HOME%\bin\java -jar admin-shell\target\admin-shell-1.0.0.jar --server.port=%PORT% --framework.storage.rocksdb.path=data\rocksdb3
goto :eof

:check
curl -s -m 5 -o nul -w "%%{http_code}" http://localhost:%PORT%/api/plugins | findstr "200" >nul
if %errorlevel%==0 (echo OK  后端在线 :%PORT%) else (echo FAIL 后端未就绪 :%PORT% & exit /b 1)
goto :eof
