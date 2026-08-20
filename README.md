<p align="center">
  <img src="icon.png" alt="亮不亮" width="200" />
</p>

<h1 align="center">亮不亮</h1>

<p align="center">极致简洁的屏幕亮度与熄屏时长调节工具，支持后台持续生效。</p>

<p align="center">在阅读、观影或任何需要固定屏幕亮度与熄屏时长的场景下，一键锁定你的屏幕设置。</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Target SDK" src="https://img.shields.io/badge/Target%20SDK-36-3DDC84">
  <img alt="ABI" src="https://img.shields.io/badge/ABI-arm64--v8a-3DDC84">
</p>

<p align="center">
  <a href="README_EN.md">English</a>
</p>

---

## 功能

- **屏幕亮度调节** — 0.1%~10% 精细调节，启用后覆盖系统亮度
- **熄屏时长控制** — 提供 5s ~ **常亮** 25个档位
- **后台守护** — 开启后通过前台服务在后台持续维持设置，切换应用也不会丢失
- **一键恢复** — 关闭功能后自动还原你的原始系统设置

## 下载

- [GitHub Releases](https://github.com/Tinger-X/liangbuliang/releases) — 下载最新版 APK
- 访问官方网站：[https://liangbuliang.tin.edu.kg](https://liangbuliang.tin.edu.kg)

## 授予 WRITE_SECURE_SETTINGS 权限（推荐）

在 Android 12 及以上系统中，应用通过系统原生的「Extra Dim（降低亮色）」功能实现 0.1%~1% 之间的超低亮度调节。该功能需要 `WRITE_SECURE_SETTINGS` 权限，而它属于系统保护权限，无法通过应用内弹窗申请，需通过 ADB 手动授予。

安装应用并开启 USB 调试后，执行以下命令：

```bash
adb shell pm grant kg.edu.tin.liangbuliang android.permission.WRITE_SECURE_SETTINGS
```

> 也可通过 Shizuku 等工具在设备上授予该权限。

- **获取该权限后**：应用会使用系统原生的「Extra Dim」平滑调节 0.1%~1% 的超低亮度，亮度变化连续、无跳变；即使 App 后台被清理，1% 以下的亮度控制仍会保留。
- **未获取该权限时**：应用会退化为「屏幕遮罩（Overlay）」方案来实现超低亮度；当 App 后台被清理时遮罩层会失效，1% 以下的亮度会回弹到 1%。

如需撤销权限：

```bash
adb shell pm revoke kg.edu.tin.liangbuliang android.permission.WRITE_SECURE_SETTINGS
```

## 构建

### 调试版

```bash
./gradlew assembleDebug
```

APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

### 发布版

1. 生成签名密钥库（如已有则跳过）

```bash
keytool -genkey -v -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

2. 设置环境变量

```bash
# Windows
set KEYSTORE_PATH=D:\path\to\my-upload-key.jks
set STORE_PASSWORD=你的密码
set KEY_PASSWORD=你的密码

# macOS / Linux
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=你的密码
export KEY_PASSWORD=你的密码
```

3. 构建

```bash
./gradlew assembleRelease
```

APK 路径：`app/build/outputs/apk/release/app-release.apk`

## 环境要求

- Android Studio Ladybug (2024.2.1) 或更高
- JDK 17+
- Android SDK 36

## 隐私说明

本应用涉及以下权限与数据：

| 权限/数据 | 用途 |
|:---|:---|
| `WRITE_SETTINGS` | 调节系统屏幕亮度与熄屏时长 |
| `WRITE_SECURE_SETTINGS` | 在 Android 12+ 通过「Extra Dim」实现 0.1%~1% 超低亮度平滑调节 |
| `FOREGROUND_SERVICE` | 后台持续维护用户设置 |
| `POST_NOTIFICATIONS` | 前台服务通知 |
| `WAKE_LOCK` | 保持屏幕常亮（用户主动选择时） |

所有设置数据仅存储在设备本地，不上传至任何服务器。

---

*Made with ❤️ by Tinger, email: liangbuliang@tin.edu.kg*
