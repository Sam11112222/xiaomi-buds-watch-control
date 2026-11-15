# 耳机控制 · xiaomi-buds-watch-control

在手表上直接控制小米 / REDMI 耳机 —— 不需要手机。
Control Xiaomi / REDMI earbuds straight from an Android-based smartwatch, no phone needed.

<p align="center"><img src="docs/icon.svg" width="120" alt="icon"></p>

## 适配设备 / Tested on
| | 型号 |
|---|---|
| 手表 | OPPO Watch X2（ColorOS Watch，Android 11，466×466 圆屏，表冠） |
| 耳机 | REDMI Buds 8 Pro（VID 0x2717 / PID 0x50E3，固件 1.2.3.7） |

其他使用同一套小米耳机协议的型号可能可以使用（未测试）；耳机不支持的功能会自动隐藏。

## 功能
已在真机上逐项验证（写入 → 耳机返回成功 → 回读一致）：

- 电量（左 / 右 / 充电盒）、佩戴状态、固件版本、序列号
- 降噪 / 通透 / 关闭，降噪强度，智能动态降噪，个性化降噪
- EQ 预设、空间音频（头部追踪、虚拟环绕）、沉浸音效场景、自适应听感、自适应音量
- 手势动作（单击 / 双击 / 三击 / 长按 / 滑动，左右耳分别设置）、长按切换的降噪模式
- 查找耳机（左 / 右 / 双耳响铃）
- 游戏低延迟、自动接听
- **「降噪切换」快捷入口**：点一下在降噪 ⇄ 通透之间切换，约 0.8 秒完成

尚未完全验证：自定义 10 段 EQ（耳机接受写入，但回读仍为平直曲线）、双设备连接开关、耳塞贴合度检测、提示音音量。
不包含：固件升级、小爱语音、翻译、账号 / 云端功能。

## 安装
1. 从 [Releases](../../releases) 下载 APK。
2. 手表开启 开发者选项 → ADB 调试（或无线调试），然后：
   ```
   adb install -r -g BudsWatch-v1.1.0.apk
   ```
3. 在手表「设置 → 蓝牙」中连接耳机。若耳机同时连着手机，请先在耳机设置里打开 **双设备连接**。
4. 打开「耳机控制」。

## 关于卡片屏
OPPO 手表的卡片屏只加载系统白名单内的卡片，第三方应用无法添加卡片（钉钉、京东等的小组件同样不显示）。
因此提供独立的「降噪切换」启动入口，可放入卡片屏的快捷应用区。
项目中还包含标准 AppWidget 卡片（适用于允许第三方小组件的桌面）和 Google Wear OS 磁贴（仅编译通过，未实机测试）。

## 技术说明
- 控制通道：经典蓝牙 RFCOMM，服务 UUID `0000FD2D-0000-1000-8000-00805F9B34FB`，无需认证。
- 帧格式：`FE DC BA | flags | opcode | len(2) | body | EF`；配置读写 opcode `0xF3` / `0xF2`，降噪模式切换 opcode `0x08`。
- 纯 Android 框架 API 实现（Wear OS 磁贴部分除外），minSdk 26。
- 协议自检（无需 Android 设备）：`app/src/test/java/ProtocolSelfTest.java`，33 项用真实数据帧校验。

构建与调试详见 [docs/BUILD.md](docs/BUILD.md)。

## 声明
本项目为个人兼容性研究，与小米、OPPO 无任何关联。“Xiaomi”“REDMI”“OPPO”为其各自所有者的商标。
协议信息来自对设备通信的互操作性分析，仅用于控制用户自己的设备。使用风险自负。
