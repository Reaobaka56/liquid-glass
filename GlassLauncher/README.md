# 🧊 Liquid Glass Launcher

A modern, minimalist, frosted-glass Android Home Screen built natively with **Kotlin** and **Jetpack Compose**.

![Android](https://img.shields.io/badge/Android-35-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.12.01-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min_SDK-26_(Android_8.0)-blue?style=for-the-badge)

---

## ✨ Key Features

* **🧊 Real Backdrop Blur:** Dynamically samples and blurs wallpaper behind UI panels on Android 12+ (`RenderEffect`).
* **📱 Minimalist Home Layout:**
  * Clean Clock & Date widget.
  * 4-App Quick Dock (Phone, Messages, Browser, Camera defaults).
* **🔍 Searchable Glass App Drawer:** Smooth swipe-up gesture with real-time app filtering.
* **🖼️ Custom Wallpaper Picker:** Long-press home screen to pick a custom wallpaper. Features a built-in Aurora gradient fallback when no wallpaper is selected.
* **⚡ Light & Efficient:** Optimized Compose rendering pipeline; panels only blur their bounded surface area.
* **🎨 Elegant Fallbacks:** Tinted translucent glass aesthetic for Android 11 and below.

---

## 🛠️ Architecture & Tech Stack

Built following modern Android development practices and Jetpack guidelines:

* **Language:** Kotlin 2.1.0
* **UI Toolkit:** Jetpack Compose with Material 3 (`androidx.compose.material3`)
* **State Management:** Android ViewModel & StateFlow / Coroutines
* **System Integration:** PackageManager & Intent handlers for default launcher integration (`CATEGORY_HOME`)

### File Map

```text
app/src/main/java/dev/glass/launcher/
├── MainActivity.kt           # Home activity & system window insets
├── AppRepository.kt          # App query, intent resolution, launcher actions
├── LauncherViewModel.kt       # StateFlow UI state & wallpaper management
└── ui/
    ├── GlassPanel.kt         # Frosted glass effect, blur shader, border & highlight
    ├── Backdrop.kt           # Wallpaper sampling & background renderer
    ├── HomeScreen.kt          # Clock widget, dock, and gestures
    └── AppDrawer.kt          # Glass app drawer & search grid
```

---

## 🚀 Getting Started

### Prerequisites

* **Android Studio** (Ladybug / 2024.2.1 or newer)
* **JDK 17+** (JDK 21 recommended)
* **Android Device / Emulator** (Android 8.0+ / API 26+)

### Building & Running

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Reaobaka56/liquid-glass.git
   cd liquid-glass/GlassLauncher
   ```

2. **Build debug APK with Gradle:**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install to connected device:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Set as default launcher:**
   * Press the **Home** button on your device.
   * Select **Glass Launcher** → **Always**.
   * *(To reset: Go to System Settings > Apps > Default Apps > Home App)*

---

## ⚙️ Customization & Tuning

* **Adjust Blur & Opacity:** Modify `blurRadius` and `tint` properties in [`GlassPanel.kt`](app/src/main/java/dev/glass/launcher/ui/GlassPanel.kt).
* **Dock App Defaults:** Change launch intent categories in [`AppRepository.kt`](app/src/main/java/dev/glass/launcher/AppRepository.kt).

---

## 👤 Author

Developed by **[@Reaobaka56](https://github.com/Reaobaka56)** (Reaobaka Mogajane)
