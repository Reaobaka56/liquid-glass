# 🧊 Liquid Glass Launcher

A modern, minimalist, frosted-glass Android Home Screen built natively with **Kotlin** and **Jetpack Compose**.

![Android](https://img.shields.io/badge/Android-35-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack_Compose-2024.12.01-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min_SDK-26_(Android_8.0)-blue?style=for-the-badge)

---

## ✨ Key Features

* **🔮 Liquid Glass Refraction (Android 13+):** An AGSL lens bends the wallpaper around each panel's rim, with colour fringing and a specular edge highlight. Tune `refraction`, `bevel`, `dispersion` and `specular` in `GlassPanel`.
* **🎛️ Glass Material System:** One `GlassMaterial` (blur, tint/opacity, refraction, thickness, dispersion, highlight, reflection, inner shading) shared by every surface, with five presets: Clear, Frost, Smoke, Midnight, Aurora. Long-press the home screen → *Glass settings* to switch presets and adjust intensity and icon size; each preset chip is a live preview. Settings persist.
* **🛡️ Graceful Degradation:** Full (blur + AGSL lens) → Reduced (blur only: Android 12, battery saver, or a shader that fails to compile) → Minimal (tint only: Android 11 and below). The shader is compiled once per panel.
* **🧊 Real Backdrop Blur:** Dynamically samples and blurs wallpaper behind UI panels on Android 12+ (`RenderEffect`).
* **📱 Minimalist Home Layout:**
  * Clean Clock & Date widget.
  * 4-App Quick Dock (Phone, Messages, Browser, Camera defaults).
  * Real Android home-screen widgets, added through the system widget picker, persisted per page, and vertically resizable in edit mode.
* **🔍 Searchable Glass App Drawer:** Smooth swipe-up gesture with real-time app filtering.
* **🖼️ Wallpaper Picker:** Long-press the home screen and pick one photo, or up to 8 for Photo Shuffle. Features a built-in Aurora gradient fallback when no photo is selected.
* **📲 Home + Lock Wallpaper:** After picking, Android offers to set *Glass Wallpaper* (a live wallpaper) for the home and lock screens, so both show the same photo.
* **🧭 Tilt Parallax:** The photo sits slightly oversized behind the glass and shifts as you tilt the phone (accelerometer). The glass panels refract the moving picture, and the lock screen uses the same motion.
* **🔀 Photo Shuffle:** With several photos, the wallpaper steps to the next one each time the screen turns off, so every wake shows a new picture. (Needs Glass Wallpaper to be the system wallpaper.)
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
├── settings/
│   └── LauncherSettings.kt    # Persisted glass preset, intensity, icon size
├── LauncherViewModel.kt       # UI state, wallpaper loading, tilt
├── wallpaper/
│   ├── GlassWallpaperService.kt  # Live wallpaper: home + lock, parallax, shuffle
│   ├── WallpaperStore.kt      # Shared photo set and current index
│   └── TiltTracker.kt         # Accelerometer to smoothed tilt, plus Parallax constants
└── ui/
    ├── glass/
    │   ├── GlassMaterial.kt  # Material parameters + presets
    │   └── GlassShader.kt    # AGSL lens, capability probe, quality levels
    ├── GlassPanel.kt         # Glass surface: blur + lens + tint, reflections, rim
    ├── SettingsSheet.kt      # Long-press menu and glass settings sheet
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

### Adding home-screen widgets

1. Make Glass Launcher the active Home app.
2. Long-press an empty area of the home screen to enter edit mode.
3. Tap **+ Add widget**, choose a provider in Android's widget picker, and approve binding or complete any provider setup when prompted.
4. The widget is saved on the current home page. In edit mode, use **−** / **+** to adjust its height or **Remove** to detach it.

Widget binding is handled by Android's `AppWidgetManager`; the launcher does not request broad permissions to read installed packages or widget data.

---

## ⚙️ Customization & Tuning

* **Tune the material:** Edit the preset values in [`GlassMaterial.kt`](app/src/main/java/dev/glass/launcher/ui/glass/GlassMaterial.kt), or the shader in [`GlassShader.kt`](app/src/main/java/dev/glass/launcher/ui/glass/GlassShader.kt).
* **Dock App Defaults:** Change launch intent categories in [`AppRepository.kt`](app/src/main/java/dev/glass/launcher/AppRepository.kt).

---

## 👤 Author

Developed by **[@Reaobaka56](https://github.com/Reaobaka56)** (Reaobaka Mogajane)
