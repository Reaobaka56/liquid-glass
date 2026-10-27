# Glass Launcher

A frosted-glass Android home screen built with Kotlin and Jetpack Compose.

- Real backdrop blur: each panel blurs a copy of the wallpaper behind it (Android 12+).
- Clock card, 4-app dock (phone, messages, browser, camera when defaults exist), searchable app drawer.
- Long-press the home screen to pick a wallpaper. Without one, a built-in aurora gradient is used.
- Android 11 and below: panels fall back to a darker translucent tint (no blur).

## Run it

1. Install Android Studio (Ladybug or newer) and open this folder.
2. Let Gradle sync. If it asks, accept the suggested plugin or version updates.
3. Plug in your phone with USB debugging on, pick it, press Run.
4. Press Home on the phone, choose "Glass Launcher", then "Always" to make it the default.
   To go back, Settings > Apps > Default apps > Home app.

No `gradlew` is included. Android Studio handles that on sync, or run `gradle wrapper` once
if you want to build from the command line.

## Where things are

| File | What it does |
|---|---|
| `ui/GlassPanel.kt` | The glass effect: blurred wallpaper copy, tint, highlight, edge border |
| `ui/Backdrop.kt` | Draws the wallpaper and shares it with the panels |
| `ui/HomeScreen.kt` | Clock card, dock, swipe-up and long-press gestures |
| `ui/AppDrawer.kt` | Glass app drawer with search, and the app icon cell |
| `AppRepository.kt` | Lists installed apps, picks dock apps, launches apps |
| `LauncherViewModel.kt` | App list, dock, wallpaper loading |

## Tuning

- Blurrier or clearer glass: `blurRadius` and `tint` on `GlassPanel`.
- Slow on your phone: lower `blurRadius`, or use fewer panels on screen.
- Panels only blur their own area, so cost scales with panel size.
