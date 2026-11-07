# Liquid Glass Launcher — audit, feasibility and plan

Target: HONOR X5b, Android 14, MagicOS 8.0 (entry-level hardware: budget GPU for every effect).

## 1. Audit of the current code

**Real today**
- Default home app (`CATEGORY_HOME`, `RoleManager.ROLE_HOME` request), survives reboot because Android restarts the HOME app.
- Home pages, dock, app drawer with search + A-Z index, recents, per-app shortcuts, uninstall/app info.
- Real AppWidgets through `AppWidgetHost` (pick, bind, configure, resize, remove).
- Live wallpaper service (home + lock) with tilt parallax and photo shuffle, EXIF-correct decode.
- AGSL glass lens (refraction, dispersion, specular, bevel) with Full / Reduced / Minimal fallback.
- Persistent settings, layout and widgets.
- Editable launcher clock with persisted font, size, weight, color, alignment, letter spacing, and system/12/24-hour formatting.
- Companion wallpaper preview uses those clock settings and supports dragging the preview clock; preview time controls do not change device time.
- Apply flow opens Android's wallpaper confirmation/default-home role and checks the active wallpaper service and Home role before reporting those states.
- Persistent clock/calendar/battery/photo/weather/music widgets can be added inside edit mode, resized, and reordered. Calendar requires `READ_CALENDAR`; weather requires user consent plus coarse location and sends coordinates to Open-Meteo; music requires the user to grant notification access.
- Transparent foreground cutouts are stored as a separate wallpaper layer. The wallpaper editor previews that layer above the editable clock, allows drag/zoom/position/opacity changes, and the live wallpaper draws the layer with persisted settings.
- The icon studio discovers installed launcher apps, applies built-in iOS-inspired icon designs individually or in bulk, imports installed compatible `appfilter.xml` packs, supports custom image uploads/reset and named collections with duplicate/rename/export/restore, and persists assignments across the launcher grid, dock, drawer, and context menu. App launch targets remain their original packages. Global icon size, shape/corner radius, and label visibility are adjustable in Settings.

**Fake, partial or missing**
- "Lock screen companion" is an in-app preview Activity, not the secure lock screen. Its clock styles and drag position do not alter the MagicOS keyguard.
- No automatic foreground segmentation or brush-based mask repair. The depth layer requires a transparent cutout prepared externally.
- Android's secure lock-screen clock remains above the live-wallpaper layers; the wallpaper layer cannot draw over the system keyguard UI.
- Hosted Android widgets still depend on installed providers. Built-in widget layout is vertical and reorderable; there is no freeform grid positioning or widget stacks.
- No icon masking, folders, App Library, app badges, notification center, or cross-app status bar replacement. Third-party packs must be installed, expose a compatible `appfilter.xml`, and will map only apps listed in that file. Launcher icon replacements do not affect Android Settings, notifications, or other launchers.
- Apply verifies the default Home role and active live-wallpaper service; it cannot confirm whether MagicOS also assigned that wallpaper to its lock-screen surface.
- No themes app (Discover / My Themes / theme bundle import).
- Clock editor controls are interactive; other launcher settings still use standard Compose controls.

**Why it does not read as Liquid Glass yet**
- Panels refract only a wallpaper copy, never what is on top of them, and nothing merges or morphs.
- Tint is a fixed colour, not adaptive to what is behind the panel.
- Motion is (after the `glass-motion` branch) press/spring only: no morphing, no scroll edge blur, no stretch on the page dots.
- Typography and spacing are default Material.

## 2. What Android actually allows

| Feature | No root | Root / system | Restricted |
|---|---|---|---|
| Replace home screen, dock, drawer, icons, widgets | Yes | | |
| Icon packs (ADW/Nova `appfilter.xml` format), masks, themed icons | Yes | | |
| Wallpaper home + lock (`WallpaperManager`, live wallpaper) | Yes | | MagicOS may limit live wallpaper on lock screen: verify on device |
| Lock-screen clock behind subject | Only inside our own wallpaper layers: the system clock is drawn by the keyguard above any wallpaper, so the system clock must be hidden or the user accepts two clocks. Verify what MagicOS lock styles allow | Hooking SystemUI/keyguard | A custom Activity cannot replace the secure keyguard |
| Notifications on our own surfaces | Yes, via `NotificationListenerService` (explicit user grant) | | Cannot restyle the system shade |
| Media controls on our surfaces | Yes, `MediaSessionManager` (needs the notification-listener grant) | | |
| Charging animation | Yes, in our UI, from `ACTION_BATTERY_CHANGED` | | System charging UI untouched |
| Status bar inside the launcher | Yes, real data (battery, time, Wi-Fi/cellular state; signal bars need `READ_PHONE_STATE`) | | |
| Status bar in other apps | | Only via root/Xposed-style hooks | Not possible otherwise |
| Dynamic-Island-style pill over apps | Yes with `SYSTEM_ALERT_WINDOW` (user grants "display over other apps"); MagicOS battery management may kill the service | | |
| Quick Settings | Own `TileService` tiles only | Restyling the panel | |
| Subject segmentation | On device: ML Kit Subject Segmentation (needs Google Play services) or MediaPipe segmenter (bundled model) | | |
| Accessibility-service tricks for the lock screen | **Not used.** Rejected on security grounds | | |

Root work, if ever wanted, lives in a separate optional module and is never required.

## 3. Roadmap (each phase ends with a green CI build and an installable APK)

- **P0 Stabilise**: CI builds the APK on every push; merge `glass-motion`. *(this commit)*
- **P1 Status bar**: iOS-style bar inside the launcher with real data; optional overlay pill.
- **P2 Launcher core**: drag-and-drop, folders with open/close animation, App Library, icon masking and icon packs, badges (via notification listener), launch/return animation.
- **P3 Wallpaper Studio + depth effect**: crop/position, segmentation, mask editor, layered live wallpaper (background, clock, subject), previews, export.
- **P4 Lock Screen Studio**: clock fonts/weights/colour/position, widgets, notification and media cards, charging animation, wake/sleep transitions.
- **P5 Themes shell**: Discover, My Themes, Settings, theme file format (JSON + assets), preview + Apply.
- **P6 Glass system v2**: adaptive tint, merging/morphing panels, glass controls, scroll-edge blur, accessibility overrides.
- **P7 Optional root module** (separate, documented risks).

Performance rule for every phase: 60 fps on the X5b or the effect degrades (the Full/Reduced/Minimal ladder), never the frame rate.
