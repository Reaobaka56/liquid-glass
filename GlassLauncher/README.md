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
  * Editable launcher clock and date: font family, size, weight, color, alignment, letter spacing, and system/12/24-hour format.
  * 4-App Quick Dock (Phone, Messages, Browser, Camera defaults).
  * Persistent built-in clock, calendar, battery, photo, weather, and music cards. Resize and reorder them in edit mode.
  * Calendar events are read only after calendar permission. Weather asks before using approximate location and sends it to Open-Meteo. Music controls need the user's explicit Notification access grant.
  * Real Android app widgets remain available through the system widget picker for other providers.
* **🔍 Searchable Glass App Drawer:** Smooth swipe-up gesture with real-time app filtering.
* **🎨 Launcher icon studio:** Search installed launcher apps, apply original iOS-inspired icon artwork by app or as a full preset, import compatible installed `appfilter.xml` icon packs, upload a per-app image, and restore one or all original icons. Save, duplicate, rename, export, and restore icon collections (including uploaded artwork). Assignments are saved locally and update the home grid, dock, drawer, and app context menu; taps still launch the original package.
* **⚙️ Icon appearance settings:** Change icon size, shape/corner radius, and app-label visibility from Settings; those preferences update launcher surfaces immediately and persist locally.
* **🖼️ Wallpaper Picker:** Long-press the home screen and pick one photo, or up to 8 for Photo Shuffle. Features a built-in Aurora gradient fallback when no photo is selected.
* **🪟 Layered wallpaper editor:** Import a transparent subject cutout as a separate layer, preview it over the editable clock, drag/zoom/reposition it, and adjust opacity. Layer image and transform settings persist and are rendered by the live wallpaper.
* **📲 Home + Lock Wallpaper:** After picking, Android offers to set *Glass Wallpaper* (a live wallpaper) for the home and lock screens, so both show the same photo.
* **🧭 Tilt Parallax:** The photo sits slightly oversized behind the glass and shifts as you tilt the phone (accelerometer). The glass panels refract the moving picture, and the lock screen uses the same motion.
* **🔀 Photo Shuffle:** With several photos, the wallpaper steps to the next one each time the screen turns off, so every wake shows a new picture. (Needs Glass Wallpaper to be the system wallpaper.)
* **⚡ Light & Efficient:** Optimized Compose rendering pipeline; panels only blur their bounded surface area.
* **🎨 Elegant Fallbacks:** Tinted translucent glass aesthetic for Android 11 and below.
* **✅ Verified setup flow:** Apply guides Android's live-wallpaper confirmation and default-home role, then reports only the states it can verify.

## What Apply can and cannot change

The app is a real Android launcher. Once selected as the default Home app, its home grid, dock, app drawer, launcher clock, and hosted Android widgets are used whenever Android opens Home. The launcher configuration and clock style are stored locally and survive app/device restarts.

Applying a wallpaper opens Android's own live-wallpaper confirmation. The photo becomes the system wallpaper only if the user confirms it; device support and whether MagicOS uses it on both Home and lock screen depend on the OS. The app checks whether its live-wallpaper service is active before reporting success.

The lock-screen companion is a **preview**, not a replacement lock screen. Its clock can be styled and dragged in that preview, but Android/MagicOS owns the secure keyguard, its clock, notifications, and unlock flow. A normal app cannot move its clock behind a wallpaper subject or replace that secure UI. Doing so would require privileged/SystemUI integration (or root), and is not provided here. The depth editor accepts a transparent cutout image as its foreground layer; automatic subject detection and brush-based mask repair are not built in, so prepare the cutout in an image editor first.

The built-in clock and battery cards use live device state. Calendar events require `READ_CALENDAR`. Weather uses Open-Meteo and requests coarse location only after showing a disclosure; coordinates are sent to the weather service when requesting a refresh. The music card uses active Android media sessions and requires Notification access, a sensitive system grant that can expose other notifications. Other widgets are genuine `AppWidgetHost` widgets supplied by installed apps. Compatible third-party icon packs must be installed and expose Android `appfilter.xml`; pack artwork is used only for mapped apps. Icon assignments affect this launcher only, not Android Settings, notifications, or other launchers. Unmatched apps use their original icons. Folders and widget stacks are not implemented.

### HONOR X5b / MagicOS notes

* **No root required:** choose Glass Launcher as the Home app; grant widget binding through Android; confirm the live wallpaper in Android's wallpaper UI.
* **System-managed:** exact lock-screen wallpaper behavior, clock layout, status bar, notifications, and background-start/battery restrictions are controlled by MagicOS. The companion preview does not bypass them.
* **Not available to an ordinary app:** replacing the secure keyguard or changing the system lock-screen clock's layering/position. Root or platform signing/SystemUI changes would be required and are intentionally not attempted.
* **Optional user grants:** calendar access, approximate location for weather, and Notification access for media-session controls. Each can be denied without preventing use of the launcher.

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
├── IconCustomization.kt      # Original icon catalog and persistent app assignments
├── IconPackRepository.kt     # Installed appfilter.xml pack discovery and icon loading
├── BuiltInWidgetStore.kt     # Persistent built-in widget placement and sizing
├── CalendarRepository.kt     # Permission-gated upcoming calendar events
├── WeatherRepository.kt      # Opt-in approximate-location weather fetch
├── MediaSessionRepository.kt # Notification-access-gated playback controls
├── settings/
│   └── LauncherSettings.kt    # Persisted glass and editable clock settings
├── LauncherViewModel.kt       # UI state, wallpaper loading, tilt
├── wallpaper/
│   ├── GlassWallpaperService.kt  # Live wallpaper: home + lock, parallax, shuffle
│   ├── WallpaperStore.kt      # Photo set, foreground layer, current index
│   └── TiltTracker.kt         # Accelerometer to smoothed tilt, plus Parallax constants
└── ui/
    ├── glass/
    │   ├── GlassMaterial.kt  # Material parameters + presets
    │   └── GlassShader.kt    # AGSL lens, capability probe, quality levels
    ├── GlassPanel.kt         # Glass surface: blur + lens + tint, reflections, rim
    ├── SettingsSheet.kt      # Launcher settings and interactive clock editor
    ├── WallpaperEditorSheet.kt # Live foreground-layer and clock preview
    ├── BuiltInWidgets.kt    # Clock, calendar, battery, photo, weather, music
    ├── IconArtwork.kt       # Original built-in icon illustrations
    ├── IconEditorSheet.kt   # Installed-app icon editor and preset application
    ├── Backdrop.kt           # Wallpaper sampling & background renderer
    ├── HomeScreen.kt          # Editable clock, hosted widgets, dock, and gestures
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
3. Tap **+ Widget** for built-in clock/calendar/battery/photo/weather/music cards. The cards persist; use the arrows or drag in edit mode to reorder and the size controls to resize.
4. Calendar events require calendar access. Weather displays a location-sharing disclosure before requesting approximate location. Music controls require enabling Notification access in Android Settings.
5. Tap **+ Add widget** to choose a provider in Android's widget picker, approve binding, and complete any provider setup when prompted.

Widget binding is handled by Android's `AppWidgetManager`; the launcher does not request broad permissions to read installed packages or widget data.

### Editing wallpaper depth layers

Open **Settings → Wallpaper depth editor** and choose a transparent PNG cutout for the foreground subject. The preview places it over the editable clock and lets you drag, zoom/crop, reposition, and adjust opacity. The original background and foreground remain separate files/configuration; the live wallpaper draws both layers. Create or repair the transparent mask in an image editor first—automatic cutout generation and brush mask repair are not included.

---

## ⚙️ Customization & Tuning

* **Tune the material:** Edit the preset values in [`GlassMaterial.kt`](app/src/main/java/dev/glass/launcher/ui/glass/GlassMaterial.kt), or the shader in [`GlassShader.kt`](app/src/main/java/dev/glass/launcher/ui/glass/GlassShader.kt).
* **Dock App Defaults:** Change launch intent categories in [`AppRepository.kt`](app/src/main/java/dev/glass/launcher/AppRepository.kt).

---

## 👤 Author

Developed by **[@Reaobaka56](https://github.com/Reaobaka56)** (Reaobaka Mogajane)
