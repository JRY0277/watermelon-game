@echo off
chcp 65001 >nul
cd /d "%~dp0"

REM ===== 离线打包 APK：不需要 Gradle，也不需要联网 =====
REM 原理：直接用 Android SDK 自带的 aapt2 / d8 / zipalign / apksigner 手工打包

set SDK=%LOCALAPPDATA%\Android\Sdk
set BT=%SDK%\build-tools\36.0.0
set PLAT=%SDK%\platforms\android-37.0\android.jar
set JBR=D:\Android Studio\jbr\bin
if not exist "%JBR%\javac.exe" set JBR=%JAVA_HOME%\bin

set OUT=%~dp0offline-build

if not exist "%BT%\aapt2.exe" (
  echo 找不到 build-tools 36.0.0，请在 Android Studio 的 SDK Manager 里安装
  pause & exit /b 1
)
if not exist "%PLAT%" (
  echo 找不到 android-37 平台，请安装后再试
  pause & exit /b 1
)

rd /s /q "%OUT%" 2>nul
mkdir "%OUT%\gen" 2>nul
mkdir "%OUT%\obj" 2>nul
mkdir "%OUT%\dex" 2>nul

echo [1/6] 编译资源 ...
"%BT%\aapt2.exe" compile --dir app\src\main\res -o "%OUT%\res.zip" || goto fail

echo [2/6] 链接 APK（含 assets 里的游戏）...
REM 注意：aapt2 强制要求 manifest 里有 package 属性，而 AGP 又要求不能有，
REM 所以离线构建用 offline\AndroidManifest.xml（只多了一个 package="com.mergegame"）
"%BT%\aapt2.exe" link -I "%PLAT%" --manifest offline\AndroidManifest.xml -A app\src\main\assets -o "%OUT%\u.apk" --java "%OUT%\gen" --min-sdk-version 21 --target-sdk-version 37 --version-code 1 --version-name 1.0 "%OUT%\res.zip" || goto fail

echo [3/6] 编译 Java ...
"%JBR%\javac.exe" -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath "%PLAT%" -d "%OUT%\obj" "%OUT%\gen\com\mergegame\R.java" app\src\main\java\com\mergegame\MainActivity.java || goto fail

echo [4/6] 生成 dex ...
set CLASSES=
for /r "%OUT%\obj" %%f in (*.class) do call set CLASSES=%%CLASSES%% "%%f"
"%JBR%\java.exe" -Xmx1024M -cp "%BT%\lib\d8.jar" com.android.tools.r8.D8 --lib "%PLAT%" --min-api 21 --output "%OUT%\dex" %CLASSES% || goto fail

echo [5/6] 打入 dex 并对齐 ...
pushd "%OUT%\dex"
"%JBR%\jar.exe" uf0 "%OUT%\u.apk" classes.dex
popd
"%BT%\zipalign.exe" -f 4 "%OUT%\u.apk" "%OUT%\a.apk" || goto fail

echo [6/6] 签名 ...
if not exist "%OUT%\debug.keystore" (
  "%JBR%\keytool.exe" -genkeypair -keystore "%OUT%\debug.keystore" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" >nul 2>&1
)
"%JBR%\java.exe" -jar "%BT%\lib\apksigner.jar" sign --ks "%OUT%\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "%~dp0merge-game.apk" "%OUT%\a.apk" || goto fail

"%JBR%\java.exe" -jar "%BT%\lib\apksigner.jar" verify "%~dp0merge-game.apk" >nul 2>&1
if errorlevel 1 (
  echo 签名校验失败
  pause & exit /b 1
)

echo.
echo ======= 打包完成 =======
echo 输出：%~dp0merge-game.apk
echo 传到手机（微信/QQ 发给自己）即可安装，安装时允许「未知来源」。
echo.
pause
exit /b 0

:fail
echo.
echo 打包失败，请看上面的错误信息。
pause
exit /b 1
