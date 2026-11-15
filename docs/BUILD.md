# 耳机控制 (BudsWatch) — 手表端 REDMI Buds 8 Pro 控制

目标设备：OPPO Watch X2（Android 11 / API 30，466×466 圆屏，表冠）+ REDMI Buds 8 Pro（VID 0x2717 / PID 0x50E3）。
纯 Android 框架 API，无第三方依赖，minSdk 26。

## 连接方式（实测）
- 经典蓝牙 RFCOMM，服务 UUID `0000FD2D-0000-1000-8000-00805F9B34FB`（小米 SDK 的 UUID_SPP_MIUI）。
  标准 SPP `1101` 会被耳机拒绝；小米手机上官方 App 走的是 MIUI 系统私有的 "MMA" 服务，其他手表上没有。
- 无需认证握手；帧格式 `FE DC BA | flags | opcode | len(2) | body | EF`。
- 耳机音频刚连上的几秒内 RFCOMM 会被拒绝，应用会自动重试 2 次，最后才回退到 BLE。

## 在手表上实测通过的功能（2026-11-15）
降噪模式（关/降噪/通透）、降噪档位、智能动态降噪、个性化降噪、EQ 预设、空间音频、沉浸音效场景、
自适应听感、自适应音量、游戏低延迟、自动接听、各手势动作映射、长按降噪循环、查找耳机（响铃/停止）、
电量 / 佩戴状态 / 固件版本 / 序列号读取。均为「写入 → 耳机返回 status 0 → 回读一致」。

未完全验证：自定义 EQ —— 耳机接受写入（status 0），但回读仍为平直曲线，需要用耳朵确认效果。
未实现：固件升级、小爱语音、翻译、账号/云端功能。

## 表盘旁的快捷控制（v1.1.0）
- **OPPO / ColorOS Watch 卡片屏**：启动器在加载卡片时用硬编码白名单（`q7/b`）过滤包名，
  `requestPinAppWidget` 也只接受 `com.heytap.wearable.instant`。第三方卡片无法出现在卡片屏上（钉钉、京东的卡片同样不显示）。
  因此提供独立的启动入口 **「降噪切换」**（`QuickToggleActivity`）：点一下在降噪 ⇄ 通透之间切换，圆形提示后自动关闭。
  可加入卡片屏的快捷应用区，或从应用列表打开。实测冷启动 0.6 s 连上、0.8 s 耳机确认。
- **标准 AppWidget 卡片**（`AncCardProvider`，217×88dp）：在允许第三方小组件的手表桌面上可用。
- **Google Wear OS 磁贴**（`AncTileService`）：仅编译通过，本表无磁贴宿主，未实机测试。

## 构建
```
set JAVA_HOME=<JDK 17 路径>
gradlew.bat assembleRelease
```
需要 JDK 17 和 Android SDK（platform 34、build-tools 34），在 `local.properties` 里写 `sdk.dir=<SDK 路径>`。

如果 Windows 上 Gradle 报 "Unable to establish loopback connection"，先设置一个短路径的临时目录：
`set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\tmp`

## 安装
手表放在充电座上用 USB 调试：`adb install -r -g app\build\outputs\apk\release\app-release.apk`
注意：OPPO 手表在充电座上会显示充电界面并覆盖应用，取下手表后再使用。

## 协议自检（不需要 Android）
```
javac -encoding UTF-8 -d out app\src\main\java\dev\budswatch\protocol\*.java app\src\test\java\ProtocolSelfTest.java
java -cp out ProtocolSelfTest
```

## 调试钩子（仅 debug 包）
`adb shell am start -n dev.budswatch.earbuds/dev.budswatch.ui.MainActivity --es bw_cmd "set 11 0102"`
支持 `anc N`、`set ID HEX`、`get ID[,ID]`；release 包会忽略。
