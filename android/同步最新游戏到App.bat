@echo off
chcp 65001 >nul
cd /d "%~dp0"

set SRC="..\merge-game\合成大西瓜.html"
set DST="app\src\main\assets\game.html"

if not exist %SRC% (
  echo 没找到 ..\merge-game\合成大西瓜.html
  pause
  exit /b
)

copy /y %SRC% %DST%
echo.
echo 已同步到 app\src\main\assets\game.html
echo 提醒：如果之前填过排行榜地址，请重新编辑 game.html 开头的 window.API_BASE
pause
