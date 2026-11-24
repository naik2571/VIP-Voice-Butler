# VIP Voice Butler

> **Hands-free notification whisperer that announces incoming VIP messages via Text-to-Speech.**

An enterprise-grade, privacy-first Android application developed in modern Kotlin and Jetpack Compose/Views. Built with strict adherence to modern Android architecture, lifecycle awareness, and hardware sensor efficiency.

---

## 🚀 Key Features

- NotificationListenerService filters messages exclusively from prioritized VIP contacts.
- Text-To-Speech (TTS) whisper engine tuned for earbuds and Bluetooth audio.
- Smart driving & audio-focus detection to avoid interrupting phone calls.
- Configurable VIP allowlist with regex pattern matching.

---

## 🛠️ Hardware & Sensor Architecture

* **Sensors & APIs:** `NotificationListenerService, Text-To-Speech Engine, AudioManager`
* **Declared Permissions:** `BIND_NOTIFICATION_LISTENER_SERVICE, POST_NOTIFICATIONS, BLUETOOTH`
* **Target SDK:** Android 14 (API 34)
* **Minimum SDK:** Android 8.0 (API 26+)
* **Architecture:** MVVM / Unidirectional Data Flow (UDF) with Coroutines & StateFlow

---

## 🔒 Privacy & Permission Transparency

This application is engineered under the **Zero Unnecessary Permissions** doctrine:
* **No Hidden Trackers:** Contains zero advertising SDKs or third-party behavioral analytics.
* **No Accessibility Privilege Misuse:** All background operations utilize sanctioned Android APIs (`WorkManager`, `ForegroundService`, `JobScheduler`).
* **Air-Gapped Operation:** Runs locally on your device hardware without requiring external cloud servers.

---

## 📦 Building and Running

### Prerequisites
* Android Studio Iguana / Jellyfish / Ladybug or newer
* Android SDK 34
* JDK 17+

### Build APK via CLI
```bash
# Clone the repository
git clone https://github.com/naik2571/VIP-Voice-Butler.git
cd VIP-Voice-Butler

# Build Debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Licensed under the [Apache License 2.0](LICENSE).
