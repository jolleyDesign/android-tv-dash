# Dash — Google TV launcher

Personal home-screen replacement for Alex's **Google TV Streamer 4K**. Priorities: **simplicity, speed, efficiency.**
Sideloaded today; Play Store is a "strong maybe" later, so don't paint into a corner (package id is permanent).

Read first: `docs/STATUS.md` (where things stand + next steps), `docs/DECISIONS.md` (why things are the way
they are), `docs/DEVICE.md` (what has been changed on the real TV and how to undo it).

## Working agreements

- Never commit/push (the folder isn't a git repo yet). Leave changes for Alex to review.
- Ask before anything outward or hard to reverse on the **real TV** (disabling packages, installing third-party
  APKs). The emulator is a throwaway test device.
- Don't guess on product/UX decisions; ask. Concise explanations; walk through big changes before editing.

## Stack

- Kotlin + Jetpack Compose **foundation/ui only** (no Material, no `tv-material`), single activity, ~2 MB APK.
- AGP 9.4.1 (built-in Kotlin), Kotlin/Compose plugin 2.4.21, Gradle 9.8.1, Compose BOM 2026.09.00.
- `compileSdk 37` (required by Compose BOM), `targetSdk 36`, `minSdk 31`. Package/namespace `dev.jolley.dash`.
- Only external dependency: `io.nayuki:qrcodegen`. HTTP via `HttpURLConnection`, JSON via `org.json`, prefs via
  `SharedPreferences` — keep it that way unless there's a strong reason.

## Commands

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # system Java 26 is too new
./gradlew :app:assembleRelease          # R8-minified, signed with the debug key (sideload)
tools/deploy.sh                         # build + install to the Streamer (+ grants usage access)
tools/deploy.sh emulator                # same, to the Google TV emulator
~/Library/Android/sdk/emulator/emulator -avd Dash_GoogleTV_1080p   # Google TV, API 34, 1080p, keyboard on
```

The Streamer connects over wireless adb (re-pair if needed — see `docs/DEVICE.md`). Find it with
`adb devices -l | grep kirkwood`; transport ids change on every reconnect. In zsh, wrap adb in a function
(`tv() { adb -t <id> "$@"; }`) — `A="adb -t 1"; $A …` doesn't word-split.

## Architecture (app/src/main/java/dev/jolley/dash)

- `Controller.kt` — **all state and all input.** One focus state machine (`zone`, `dockIndex`, `showIndex`,
  `overlay`) instead of Compose's focus tree; the root `Box` is the only focusable and forwards every key to
  `Controller.onKey`. Long-press = OK key auto-repeat (`repeatCount ≥ 1`) or the Menu key. Back is always consumed
  (a HOME activity must never finish).
- `ui/Root.kt` — overrides `LocalDensity` so **1.dp = 1 design pixel on a 1920×1080 canvas**, scaled to the
  screen. Design measurements from the mockups carry over 1:1.
- `ui/Home.kt` — clock + weather, pinned shows row, glass dock, "All apps" hint. `ui/Overlays.kt` — All apps
  sheet, options menu, settings panel, phone/QR screen, notices. `ui/Theme.kt` — color tokens, Geist font,
  wallpapers, focus ring, Lucide icons as `ImageVector`s.
- `ui/Motion.kt` — one shared ambient clock (`LocalMotion`) read only in draw phase: wallpaper drift,
  breathing ring, sheen, gliding shows ring, rolling clock digits, animated weather icons. Gated by the
  "Ambient motion" pref; the clock only ticks while Home is visible.
- `data/Apps.kt` — app list (LEANBACK_LAUNCHER first, then LAUNCHER) + circular icon rendering (adaptive layers,
  legacy icons on a disc, banner center-crop fallback). `data/Store.kt` — prefs. `data/Net.kt` — Open-Meteo,
  usage stats, show-link handler resolution + og:title/og:image scraping, image decode helpers, LAN IP.
- `net/PinServer.kt` — LAN-only HTTP server for the phone companion page. **Runs only while the QR screen is
  open**; every request needs the random token in the QR URL. Port 8642.

## Design

- Tokens: bg `#0B0B0A`, panel `#1A1917`, text `#F2EEE6`, muted `#A39D92`, accent **blue `#5AA9FF`** (amber was
  rejected), on-accent `#04121F`, bezel `#3A3732`. Font: Geist (bundled variable TTF). Icons: Lucide paths.
- `design/mockup.html` (interactive; arrows + 1–6), `design/screens/*.png` (Pencil exports).
- Keep the **top-right corner empty** — Android TV places PiP windows there.

## Testing tricks

- Emulator keys: arrows = D-pad, Enter = OK, ⌘M = Menu (options), Esc = Back, ⌘⇧H = Home.
- Drive with `adb shell input keyevent 19/20/21/22/23/4/3`; long-press with `input keyevent --longpress 23`.
- Phone flow without a phone: screenshot (`adb exec-out screencap -p`), decode the QR with
  `swift tools/qr-decode.swift shot.png`, `adb forward tcp:18642 tcp:8642`, then
  `curl -d "zip=84101" "http://127.0.0.1:18642/zip?t=<token>"` (also `/pin` with `url=`, `/wallpaper` with raw image bytes).
- Perf baseline (emulator): cold start ~225 ms, ~47 MB PSS.
