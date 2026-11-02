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
| Dock | Glassy/translucent pill: favorites │ recents │ shortcuts | Alex asked for glass |
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
| Motion | Ambient motion on by default, one toggle; no video wallpapers | Alex wanted more life; video rejected for decoder contention (PiP), per-frame blur cost and APK size |
