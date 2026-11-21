# 第三方组件与素材

## FinalBurn Neo / Libretro

- 来源：官方 Libretro buildbot 的 Android arm64 构建，未修改核心。
- 对应源码提交：`e0ecdb11af563f31e698e590d69a85f087f528b3`。
- 源码：https://github.com/libretro/FBNeo/tree/e0ecdb11af563f31e698e590d69a85f087f528b3
- 源码归档：https://github.com/libretro/FBNeo/archive/e0ecdb11af563f31e698e590d69a85f087f528b3.tar.gz
- `libretro.so` SHA-256：`82637a59dbe07f544be97c79267acafa17ffe3766bcbe0fcee5b6efd540f2e0b`。
- 原始完整条款：[FBNeo-LICENSE.txt](android/assets/FBNeo-LICENSE.txt)、[MAME-LICENSE.txt](android/assets/MAME-LICENSE.txt)。这些文本同样包含在 APK 中。

FBNeo 包含不同许可证的组件。其原始许可限制商业获利，并要求保留许可与提供/链接源码；本仓库没有以单一宽松许可覆盖第三方核心。详细权利以各组件源码中的声明为准。`host/libretro.h` 保留上游头文件中的版权和 MIT 许可。

## 游戏素材

游戏名称、角色、画面和标识属于各自权利人。仓库中的离线演示是从本地练习模式录制的教学素材；游戏 ROM 和 BIOS 不在仓库或 APK 内。JNI 前端与演示制作脚本在本仓库提供。

## 构建/测试工具

JDK、Android SDK、Zig、Gradle、JUnit、Robolectric 和 FFmpeg 为独立工具，不随仓库分发。具体版本见 README 和测试构建文件。
