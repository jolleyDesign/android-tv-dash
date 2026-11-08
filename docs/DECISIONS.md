# Decisions

Settled with Alex; don't re-litigate without a reason.

| Area | Decision | Why |
|---|---|---|
| Stack | Native Kotlin + Compose foundation/ui, no Material/tv-material | Speed, small APK, full control of focus visuals |
| Focus | Single key-driven state machine in `Controller`, one focusable root | Deterministic D-pad behavior, cheap |
| Layout | Fixed 1920×1080 canvas via `LocalDensity` override | Design px map 1:1 to code on any TV |
| Home swap | Disable `launcherx` **and** `setupwraith` via adb, plus `set-home-activity` | Google TV has no default-home setting; both stock activities outrank third-party HOME filters |
| Visual | "Vehicle dashboard" mood, dark; **blue accent `#5AA9FF`** | Amber/yellow rejected ("weird piss color") |
| Wallpaper | 8 bundled Unsplash photos (default City), Black, or a photo via phone upload | Gradients rejected as bland; +~0.8 MB APK accepted. Credits in `docs/WALLPAPERS.md` |
| Labels | Sentence case, no letter-spacing (no ALL CAPS eyebrows) | Alex: caps felt aggressive |
| Dock focus | macOS-style magnify: focused +32% and rises, neighbors grow, pill widens (spring) | Alex's pick over focused-only |
| Weather ZIP | On-screen keypad in Settings (D-pad or keyboard digits); phone page still accepts a ZIP | No QR needed for a 5-digit input |
| Legacy icons | Solid shapes (square/squircle) are scaled to fill the circle; free-standing logos sit centered on a dark disc | Square icons broke the round dock |
| Dock | Frosted-dark pill (same blur + 58% black tint as widget cards): favorites │ recents │ shortcuts; ◀ ▶ wrap around | Glass shine stood out too much once the cards went dark |
| All apps | No dock button — **↓ on dock slides up the sheet**, small "⌄ All apps" hint | Alex's call |
| Empty shows | Shows row keeps only the "Pin a show" tile | A "More apps" row was tried and rejected |
| Weather | Next to the clock; Open-Meteo (no key); location = ZIP set once (no IP geolocation); °F | Privacy + accuracy |
| Recents | UsageStatsManager; permission granted once via adb appops | Catches apps opened by voice/deep links |
| Pinned shows | Deep-link tiles added from phone (QR → LAN page) | Watch Next data is restricted on Google TV |
| Phone server | Runs only while QR screen is open; random token required | Nothing else on the LAN can push to the TV |
| PiP | Launcher keeps top-right empty; can't force other apps into PiP | PiP is opt-in per app |
| YouTube | Official YouTube TV app has **no PiP** and no background play (manifest checked, v7.28.301) → SmartTube installed | Alex chose SmartTube knowing it's sideloaded and blocks ads |
| Gear button | Opens Dash's settings; Android settings is a row inside | Open question: could flip to gear → Android settings, long-press → Dash settings |
| Name / id | "Dash" / `dev.jolley.dash` | Permanent if published |
| Signing | Release signed with debug key | Sideload only; real upload key needed before Play |
| Widgets | Own row of Dash-drawn cards (no AppWidgetHost); default all on; order via Move | Third-party TV widgets barely exist; keeps the look and the 1080p canvas |
| Widget area | y 300–552 (y 564 when the shows row is off), full width, last 96px fades | Fits under the tallest clock face; Alex rejected an early fade that kept clear of PiP |
| Layout | "Centered" (default) or "Left": clock, widgets row, shows row; rows longer than the screen scroll from the left | Alex wanted to try centered |
| Clock size | 1.4× when only one row (or none) is shown, width-capped to stay clear of PiP | Fills the freed space; 1.6× felt too big |
| Small wall text | Tight dark shadow for text ≤ 40sp; soft halo for the big clock | Date was unreadable on bright photos |
| Clock weather | Optional ("Weather by clock", default on) | Redundant when the Weather widget is on |
| Widget cards | Blurred wallpaper (same copy as the dock) under a 58% black tint, hairline edge, no sheen | Alex: full glass shine too strong, plain translucent too hard to read |
| Bluetooth widget | Folded into "Connections" (status only); connecting happens in Android settings | Third-party apps can't connect/disconnect BT devices (needs privileged permission) |
| BT detection | Bonded devices + non-SDK `isConnected()`/`getBatteryLevel()` via reflection | No public API for "is this device connected"; both are on the allowed non-SDK list |
| Now Playing | NotificationListenerService (granted over adb) → MediaSessionManager | Only public route to other apps' media sessions |
| Clock faces | Thin, Bold, Analog, Stacked, Words; one setting, ◀ ▶ to step | Alex picked all four extra faces |
| Focus ring | 2.5dp accent at 70% alpha, faint halo. App circles: flush, no gap. Tiles/cards: 6dp gap | Alex: no padding on app circles; full-strength 4dp ring "stands out like a sore thumb" |
| Motion | Ambient motion on by default, one toggle; no video wallpapers | Alex wanted more life; video rejected for decoder contention (PiP), per-frame blur cost and APK size |
