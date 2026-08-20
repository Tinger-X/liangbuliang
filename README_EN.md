<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_icon_light.png" alt="LiangBuLiang" width="200" />
</p>

<h1 align="center">LiangBuLiang</h1>

<p align="center">An ultra-simple screen brightness and screen-off timeout adjustment tool that keeps working in the background.</p>

<p align="center">Lock your screen settings with one tap for reading, watching videos, or any scenario where you need a fixed screen brightness and screen-off timeout.</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Target SDK" src="https://img.shields.io/badge/Target%20SDK-36-3DDC84">
  <img alt="ABI" src="https://img.shields.io/badge/ABI-arm64--v8a-3DDC84">
</p>

<p align="center">
  <a href="README.md">中文</a>
</p>

---

## Features

- **Screen brightness adjustment** — fine-grained control from 0.1% to 10%, overriding the system brightness once enabled
- **Screen-off timeout control** — 25 levels from 5s to **Always On**
- **Background daemon** — keeps your settings applied in the background via a foreground service, so they won't be lost when switching apps
- **One-tap restore** — automatically restores your original system settings when the feature is turned off

## Download

- [GitHub Releases](https://github.com/Tinger-X/liangbuliang/releases) — download the latest APK
- Visit the official website: [https://liangbuliang.tin.edu.kg](https://liangbuliang.tin.edu.kg)

## Granting the WRITE_SECURE_SETTINGS permission (recommended)

On Android 12 and above, the app uses the system's native "Extra Dim" feature to achieve ultra-low brightness between 0.1% and 1%. This feature requires the `WRITE_SECURE_SETTINGS` permission, which is a system-protected permission and cannot be requested through an in-app dialog, so it must be granted manually via ADB.

After installing the app and enabling USB debugging, run the following command:

```bash
adb shell pm grant kg.edu.tin.liangbuliang android.permission.WRITE_SECURE_SETTINGS
```

> It can also be granted on the device via tools such as Shizuku.

- **After the permission is granted**: the app uses the system's native "Extra Dim" to smoothly adjust ultra-low brightness from 0.1% to 1%, with continuous, jump-free brightness changes; even if the app's background process is killed, brightness control below 1% is preserved.
- **Without the permission**: the app falls back to a "screen overlay" approach to achieve ultra-low brightness; when the app's background process is killed, the overlay becomes ineffective and brightness below 1% snaps back to 1%.

To revoke the permission:

```bash
adb shell pm revoke kg.edu.tin.liangbuliang android.permission.WRITE_SECURE_SETTINGS
```

## Build

### Debug build

```bash
./gradlew assembleDebug
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`

### Release build

1. Generate a signing keystore (skip if you already have one)

```bash
keytool -genkey -v -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

2. Set environment variables

```bash
# Windows
set KEYSTORE_PATH=D:\path\to\my-upload-key.jks
set STORE_PASSWORD=your-password
set KEY_PASSWORD=your-password

# macOS / Linux
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=your-password
export KEY_PASSWORD=your-password
```

3. Build

```bash
./gradlew assembleRelease
```

APK path: `app/build/outputs/apk/release/app-release.apk`

## Requirements

- Android Studio Ladybug (2024.2.1) or later
- JDK 17+
- Android SDK 36

## Privacy

This app involves the following permissions and data:

| Permission/Data | Purpose |
|:---|:---|
| `WRITE_SETTINGS` | Adjusts system screen brightness and screen-off timeout |
| `WRITE_SECURE_SETTINGS` | Achieves smooth 0.1%~1% ultra-low brightness via "Extra Dim" on Android 12+ |
| `FOREGROUND_SERVICE` | Keeps user settings applied in the background |
| `POST_NOTIFICATIONS` | Foreground service notification |
| `WAKE_LOCK` | Keeps the screen on (only when the user actively chooses) |

All settings data is stored locally on the device only and is not uploaded to any server.

---

*Made with ❤️ by Tinger, email: liangbuliang@tin.edu.kg*
