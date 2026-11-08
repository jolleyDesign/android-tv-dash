# Status — 2026-11-01

## Done (v0.1.0)

Built, tested on the Google TV emulator (API 34), installed on the Streamer, and set as its Home.

- Home: clock (12/24h) + weather beside it, pinned shows row, glass dock (favorites │ 2 recents │ Wi-Fi, Settings),
  "⌄ All apps" hint. ↓ on the dock slides up All apps; ↑ on its top row or Back closes it.
- Hold OK / Menu → options: Move (dock or shows), Add to / Remove from dock, Hide app, App info, Remove show.
- Settings panel (dock gear): wallpaper (photos/Black/custom photo), clock format, weather ZIP,
  recents toggle, pinned-shows toggle, hidden apps (unhide), Android settings, restore-Google-TV instructions.
- Phone companion (QR → LAN page): pin a show by link (auto title + poster via og tags), upload wallpaper,
  set weather ZIP. Token-protected; server only runs while the QR screen is open.
- Release APK ~1.9 MB; cold start ~225 ms and ~47 MB PSS on the emulator.

## Done (v0.2, installed on the Streamer 2026-11-01)

- Legacy square icons fill the dock circle; sentence-case labels; macOS-style dock magnify; 8 photo wallpapers
  (settings grid 2×5); ZIP entry via on-screen keypad. APK 2.6 MB.

## Done (v0.3 motion, emulator only — not yet on the Streamer)

- **Ambient motion** (Settings toggle, default on; off = exactly v0.2, zero frames rendered while idle):
  slow Ken Burns drift on photo wallpapers (dock glass now uses a pre-blurred low-res copy so it drifts in
  sync at no per-frame cost), animated weather icons, focus ring that breathes while you're active and settles
  after 30 s idle, a one-time sheen when focus lands, one ring that glides between pinned-show tiles, clock
  digits that roll on the minute, and the clock/shows/dock rising back in when you return from an app.
- Settings rows tightened slightly so the new row fits.

## Done (v0.4 widgets, emulator only — not yet on the Streamer)

Tested on the phone emulator forced to 1920×1080 (the Google TV AVD and its system image are gone from this
Mac; re-download `system-images;android-34;google-tv;arm64-v8a` to recreate `Dash_GoogleTV_1080p`).

- **Widgets row** between the clock and pinned shows, or in the shows' place when that row is off (frosted dark cards,
  scrolls sideways, full width with a fade at the screen edge). ↑ from shows/dock enters it; hold OK → Move / Remove; Settings → Widgets toggles each one.
  - Weather: current + feels-like/wind/humidity/UV/sun, 10-hour strip, 5-day ranges.
  - Now playing: active media session (art, title, app), OK = play/pause, menu → Open app.
  - Sun & air: sunrise/sunset arc, daylight, US AQI (Open-Meteo air-quality API).
  - World clocks: up to 3 cities (Settings → Widgets → World clock cities, or OK on the card).
  - Connections: Wi-Fi band/signal/IP/link speed, online check, connected Bluetooth devices + battery.
- **Clock faces** (Settings → Clock face, ◀ ▶): Thin (default), Bold, Analog (sweeping accent second hand when
  motion is on), Stacked (minutes in accent blue), Words.
- **Bluetooth in dock** toggle (off by default) → Android Bluetooth settings.
- App focus ring now sits flush on the icon (no gap); tiles keep the 6dp gap.
- Settings panel scrolls; APK 2.9 MB.

## Verified on the real Streamer

- Installs and runs at 1080p UI; default dock built from installed apps; Recents working (usage access granted).
- Home button → Dash (from inside apps too). Inputs shortcut correctly hidden (no VIEW_INPUTS on this device).

## Not yet verified on the Streamer

- [ ] Weather ZIP set from Alex's actual phone (worked on emulator via simulated requests).
- [ ] Pinning a **Netflix/Plex** link — opens directly in the app? poster scraped? (YouTube verified on emulator.)
- [ ] Hold-OK timing on the real remote (uses key auto-repeat; tell if too slow/fast).
- [ ] Launch scale-up animation feel.
- [ ] **SmartTube PiP behind Dash**: SmartTube 32.68 (armeabi-v7a) installed; Alex to enable its
      "Background playback" setting, play a video, press Home. Then check with
      `adb shell dumpsys activity activities | grep -i pinned` that it's in PiP and Dash stays usable.

- [ ] **v0.4**: on the TV; media access granted 2026-11-08. With shows on, check a PiP window over the right end of the widget row is acceptable. Now Playing with real apps (Spotify/SmartTube/YouTube — art + play/pause), connected-device
      detection (`isConnected`/battery are non-SDK reflection; verify the remote shows up), Wi-Fi band/signal,
      `ACTION_BLUETOOTH_SETTINGS` opens Remotes & Accessories, frame pacing with 5 glass cards + analog sweep.
- [ ] **v0.3 motion smoothness** — emulator uses SwiftShader so frame timings there are meaningless. Check
      `adb shell dumpsys gfxinfo dev.jolley.dash` on Home after a reset; tune drift speed/breath strength by feel.

## Known nits

- Muted grey labels are a bit soft over very bright photo wallpapers (shadow + scrim already added).

## Next (proposed, not started — Alex to pick)

1. **Now Playing card** — read the active MediaSession (needs notification-listener access granted via adb
   `cmd notification allow_listener`), show title/art/play-pause by the clock.
2. **Baseline profile** — faster first frames after boot/update; no UI.
3. **Backup/restore** via the phone page (dock, shows, hidden, prefs as JSON) — sideloaded installs are fragile.
4. Polish: lighter muted text on photo wallpapers, reorder in All apps, optional idle "quiet" mode.

Skipped on purpose: auto "continue watching" rows (Google restricts the data), folders, own screensaver.
