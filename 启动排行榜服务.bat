@echo off
chcp 65001 >nul
cd /d "%~dp0"

set PY=
where python >nul 2>nul && set PY=python
if "%PY%"=="" (where py >nul 2>nul && set PY=py)
if "%PY%"=="" set PY="C:\Users\G2861\.workbuddy\binaries\python\versions\3.13.12\python.exe"

echo 使用解释器: %PY%
echo 启动中，窗口不要关闭（关掉 = 服务停止）
echo.
%PY% server.py 8123
pause
