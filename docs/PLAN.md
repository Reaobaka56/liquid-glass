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

**Fake, partial or missing**
- "Lock screen companion" is an in-app preview Activity. It only appears over the keyguard if something launches it, and Android blocks background launches, so it is not a lock screen.
- No drag-and-drop UI (the move function exists, nothing calls it), no folders, no App Library, no icon packs/masking, no badges.
- No status bar of its own, no notification, media or battery integration.
- No wallpaper editor, no segmentation, no depth effect, no themes app (Discover / My Themes / Apply).
- Controls are Material `TextButton`s and sliders, not glass.

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
