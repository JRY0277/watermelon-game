# 合成大西瓜 · 打包 APK 说明

> 结论先说：**推荐直接用「离线一键打包」，不需要 Gradle，也不需要联网。**
> 已经打好的安装包：`merge-game.apk`（在本目录，约 29 KB）。

---

## 方式一：离线一键打包（推荐，10 秒出包）

双击运行：

```
build-apk-offline.bat
```

它会用 Android SDK 自带的 `aapt2 / javac / d8 / zipalign / apksigner` 手工完成打包，
**完全不经过 Gradle**，因此不会出现版本不匹配的问题。

产物：`merge-game.apk`（覆盖更新）

流程改游戏时：

1. 先双击 `同步最新游戏到App.bat`（把最新的 `合成大西瓜.html` 拷进 `app/src/main/assets/game.html`）
2. 再双击 `build-apk-offline.bat`
3. 得到新的 `merge-game.apk`

### 它依赖什么（都已具备）

| 组件 | 路径 | 状态 |
|---|---|---|
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` | 已装 |
| build-tools | `36.0.0` | 已装 |
| 平台 | `android-37.0` | 已装 |
| JDK | `D:\Android Studio\jbr` | 已装 |

---

## 方式二：Android Studio / Gradle（可选）

这条路在这台机器上有点绕，原因见下。如果要用：

1. Android Studio 打开本目录
2. 首次 Sync 时若提示安装 `android-36` 平台，点 Install（需要联网）
3. Build → Build Bundle(s)/APK(s) → Build APK(s)

### 之前那个报错是怎么回事

报错原文：

```
Unable to find method 'org.gradle.api.artifacts.Dependency
org.gradle.api.artifacts.dsl.DependencyHandler.module(java.lang.Object)'
```

根因不是缓存损坏，而是 **AGP 与 Gradle 版本不匹配**：工程里写的是 AGP 7.4 / 8.1，
而 Android Studio（AI-261）默认用的是 Gradle 9.3.0，AGP 7.x/8.x 在 Gradle 9 上会直接炸。

官方对应关系（最低 Gradle 要求）：

| AGP | 最低 Gradle | 本机是否具备 |
|---|---|---|
| 9.3 | 9.5.0 | 否（下载不到） |
| 9.2 | 9.4.1 | 否 |
| 9.1 | 9.3.1 | 否（本机是 9.3.0） |
| **9.0** | **9.1.0** | **是，用这个** |
| 8.13 | 8.13 | 否 |

已经做的修正：

- `gradle/wrapper/gradle-wrapper.properties` → 锁定 **Gradle 9.3.0**（本机已缓存，`gradle-wrapper.jar` 46 KB 已放进工程，不用重新下载）
- `build.gradle` → AGP **9.0.0**
- `app/build.gradle` → `compileSdk 36` / `targetSdk 36` / `buildToolsVersion 36.0.0`
  （AGP 9.0 最高只支持到 API 36；SDK 里目前装的是 android-37，所以这一步需要联网补装 android-36）

> 注意：本机网络只能连到 `dl.google.com`，`services.gradle.org` 和 Maven Central 连不上，
> 所以 Gradle 发行版下不下来 —— 这也是为什么推荐方式一。

---

## 装到手机上

1. 把 `merge-game.apk` 发到手机（微信 / QQ 发给自己、或 USB 拷进去）
2. 手机上点开安装，提示「未知来源」时允许
3. 安装完打开即可玩，**离线可玩**（游戏本体在 APK 的 assets 里）

排行榜说明：APK 里没填服务器地址时，排行功能会自动隐藏；
想让手机上也能上榜，编辑 `app/src/main/assets/game.html` 开头的 `window.API_BASE`，
填上你电脑的局域网地址（例如 `http://192.168.1.20:8123`），再重新打包。

---

## 常见问题

**Q：双击 bat 一闪而过？**
看上面的错误信息。最常见的是 SDK 路径不同——改 bat 里的 `set SDK=` 一行。

**Q：改了 `AndroidManifest.xml` 之后要注意什么？**
离线打包用的是 `offline/AndroidManifest.xml`，它只比原文件多一个 `package="com.mergegame"`。
改了原 manifest 的话，把这一行同步加过去。
（原因：aapt2 强制要求 manifest 有 package，而 AGP 9 又强制要求不能有，只能留两份。）

**Q：能换图标吗？**
替换 `app/src/main/res/mipmap-*/ic_launcher.png` 五个尺寸的图，再重新打包。

**Q：APK 签名用的是什么？**
首次打包时脚本自动生成一个调试密钥 `offline-build/debug.keystore`（有效期 30 年）。
自己用足够了；要上架的话再换成正式签名。
