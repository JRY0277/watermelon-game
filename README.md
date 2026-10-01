# 🍉 合成大西瓜 · 自定义图片版

一个可以**换成任意图片**的合成大西瓜：把水果换成爱豆、表情包、自家猫、产品 logo…… 相同图片碰到一起就升级。

- **网页版**：一个 HTML 文件，双击就能玩，零依赖
- **手机版**：可打包成 APK，离线可玩
- **排行榜**：本机榜（离线可用）+ 联网榜（可选后端）

---

## 快速开始

### 电脑上玩

双击 `合成大西瓜.html`（用 Chrome / Edge / 火狐 打开即可）。

- 鼠标移动选位置，**点击投放**
- 相同图片碰到一起 → 升级成下一级
- 球**稳稳停**在橙色虚线上方约 1.2 秒才算结束，期间虚线会变红提醒你

### 手机上玩

用 `android/` 目录里的工程打包，或直接装打好的 `merge-game.apk`（见 Release）。
装的时候允许「未知来源」即可。

---

## 换成自己的图片

1. 点右上角 **🎨 换图片**
2. 点第 1 个格子 → 从相册/文件夹里选一张图
3. 依次点第 2、3… 格，把想用的图按顺序放进去
4. 点 **完成并重开**

规则：**从第 1 格起连续**的才算数。放 6 张图，游戏就是 6 级；最多 11 级。
图片会自动缩到 320px 存在浏览器本地，下次打开还在。

> 手机上打不开相册？Android 版的 WebView 需要实现 `onShowFileChooser` 才能选图，`android/app/src/main/java/com/mergegame/MainActivity.java` 里已经写好了。

---

## 排行榜

分两层，点 **🏆 排行** 查看：

| 榜单 | 存储位置 | 需要服务器吗 |
|---|---|---|
| **本机榜** | 浏览器 localStorage，前 20 名 | 不需要，离线可用 |
| **联网榜** | SQLite（`game.db`） | 需要，见下 |

### 起联网榜（可选）

```bash
# 双击 启动排行榜服务.bat，或命令行：
python server.py 8123
```

然后访问 `http://localhost:8123`，游戏会自动探测到后端并显示联网榜。
同一 WiFi 下，手机访问 `http://<你电脑的局域网IP>:8123` 也能一起上榜。

- 后端只用 Python 标准库（http.server + sqlite3），**不用装任何包**
- 数据落在 `game.db`，Navicat / DB Browser for SQLite 可直接查看
- 想在 APK 里也联网上榜：把 `合成大西瓜.html` 开头的 `window.API_BASE` 改成你的局域网地址再重新打包

---

## 打包 APK

**推荐离线打包**，不需要 Gradle、不需要联网：

```
双击 android/build-apk-offline.bat
```

产物 `android/merge-game.apk`。改完游戏后先双击 `同步最新游戏到App.bat`，再打包。

它直接用 Android SDK 自带的 `aapt2 / javac / d8 / zipalign / apksigner` 组装，绕开了
AGP 与 Gradle 的版本地狱。想用 Android Studio 编译也可以，细节见
`android/打包APK说明.md`（含 AGP↔Gradle 版本对照表）。

---

## 目录结构

```
.
├── 合成大西瓜.html         游戏本体（单文件，HTML+CSS+JS 全内联）
├── server.py              排行榜后端（Python 标准库，零依赖）
├── schema.mysql.sql       想换 MySQL 时的建表语句
├── 启动排行榜服务.bat      一键起后端
└── android/               APK 工程
    ├── app/src/main/      WebView 壳 + 游戏 assets + 图标
    ├── offline/           离线打包用的 manifest
    ├── build-apk-offline.bat   一键出 APK
    ├── 同步最新游戏到App.bat    把最新 HTML 拷进工程
    └── 打包APK说明.md
```

---

## 实现说明

- **物理**：自研 PBD（Position Based Dynamics），每帧 2 子步 × 8 次约束迭代，
  质量按 r² 加权做圆-圆分离，带切向摩擦与回弹
  - 防弹飞：限制每子步位置修正量上限（`maxCorrection`）+ 速度上限
  - 边界钳制放在修正量限制**之后**做硬约束，否则球会被挤穿出去
- **渲染**：Canvas 2D，图片按 cover 裁剪成圆形贴图
- **手感可调**：页面「⚙️ 手感」面板可实时调重力 / 滑度 / 弹性，存在 localStorage
- **无构建**：游戏是单文件，没有打包步骤，改完刷新即可

---

## 已知限制

- 网页版用 `file://` 打开时，联网榜会自动隐藏（跨域限制），本机榜照常用
- APK 的联网榜需要在 `window.API_BASE` 里手填电脑的局域网地址
- 图片存在 localStorage，容量有限，建议单张图别太大
