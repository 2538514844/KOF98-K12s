# KOF98-K12s

面向 Android 横屏手机的《拳皇 ’98》前端，使用 FinalBurn Neo 模拟核心。当前版本 **v0.26**，最低 Android 8.0，仅支持 **arm64-v8a**。

## 下载与使用

从 [GitHub Releases 下载最新版 APK](https://github.com/2538514844/KOF98-K12s/releases/latest/download/KOF98-K12s.apk)。使用本仓库签名版本的用户可以直接覆盖安装，已有游戏数据、存档和按键布局保留。

首次使用需要在启动页导入兼容的 `KOF98-data.zip`。APK 和本仓库不包含游戏 ROM 或 BIOS；导入器按既有数据包清单和 SHA-256 严格校验，不能直接导入任意 ROM ZIP。已有数据包的用户可以继续使用，升级无需重新导入。

- 五种机型/地区：AES_EUR、AES_JAP、MVS_EUR、MVS_USA、MVS_JAP，独立存档。
- 八方向摇杆、A/B/C/D 多点触控及单拇指相邻双键；新增小号 MAX 键，一键同时按 A+B+C。所有控件支持位置和大小编辑，街机模式提供投币。
- 蓝牙双人对战，支持单挑和团队竞技；房主开房，另一方选择房主加入，可交换左右席位。
- 经典 4:3、全屏及保持比例设置，按键透明度可调。
- 中文出招表，51 个角色及形态、275 条指令和离线练习回放。
- 自动保存/恢复、暂停和重载游戏。
- 可选输入调试，记录触点、摇杆判定和实际核心输入，支持导出 ZIP。

**v0.26** 加入默认约七成大小的 MAX 键，升级会保留旧版按键布局。本次公开发布同时包含 v0.19–v0.25 测试版的摇杆、无式斜向采样、组合按键和输入调试改进。详见 [MAX 键说明](docs/max-button-v0.26.md)、[输入调试使用方法](docs/input-debug.md)和[更新记录](CHANGELOG.md)。

## 从源码构建（Windows）

需要 Python 3.11+、JDK 21、Android SDK Platform 35 / Build-Tools 35.0.1，以及 Zig 0.14.1。环境变量均可自行指定，不依赖原开发机器目录。

```powershell
$env:K98_SDK = 'G:\AndroidTools'
$env:K98_JDK = $env:JAVA_HOME
python -m pip install --target tooling ziglang==0.14.1
```

下载本仓库 Release 的 APK 至 `release/KOF98-K12s.apk`。从中提取经过 SHA-256 固定校验的、未修改的 FBNeo 核心：

```powershell
python scripts/prepare-runtime.py --from-apk release/KOF98-K12s.apk
python android/package.py --apk-only
```

输出：`release/KOF98-K12s.apk`。`--apk-only` 不需要游戏数据；构建将编译 Java、JNI 桥接层，进行 DEX、16 KiB ZIP 对齐和 APK v2/v3 签名。首次构建自动生成本机密钥，保存在被 Git 忽略的 `android/signing/`。自行生成的密钥不能覆盖安装由发布者密钥签名的版本。

可用 `K98_ZIG` 指定另一处 Zig 0.14.1 可执行文件。核心原始源码、固定版本与许可证见 [THIRD_PARTY.md](THIRD_PARTY.md)。仓库不保存发布签名私钥、用户存档、工具缓存或本机日志。

## 验证

```powershell
python scripts/test-host.py
python scripts/verify-apk.py release/KOF98-K12s.apk
```

Android 控件/窗口与 Canvas 测试使用 Gradle 8.12、Robolectric 4.14.1、API 28。先构建 APK，再执行：

```powershell
$env:GRADLE_USER_HOME = "$PWD/tooling/ui-gradle"
gradle -p host/settings-ui --console plain test
```

首次运行测试需要下载测试依赖。v0.26 已通过主机回归（含 172 项触屏回放场景）、37 项 Android 框架测试、12 项 MAX 桌面核心回放，以及 APK 签名、对齐和同签名升级检查。此前无式日志的 3467 帧输出与 v0.23 修复版一致。本轮尚未在手机上复测，蓝牙设备连接尚未做完整真机验证。详见 [v0.26 验证报告](docs/verification-v0.26.json)。

## 目录

| 目录 | 内容 |
|---|---|
| `android/src` | Activity、游戏画面、设置、触控和出招表 |
| `android/jni` | libretro JNI 桥接和最小 C 运行时 |
| `android/assets` | 中文指令、离线演示和第三方声明 |
| `host` | 回归测试、素材录制与编码工具、libretro 头文件 |
| `scripts` | 核心提取、测试和 APK 核验工具 |

录制工具需要单独准备本地游戏数据和 Windows 模拟核心；常规 APK 构建直接使用已提交的演示素材。此项目不是 SNK 官方手机版。
