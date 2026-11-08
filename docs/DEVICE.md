# Google TV Streamer — current state

Model `Google_TV_Streamer` (device `kirkwood`), Android 14, 4K panel with a 1920×1080 UI override,
density 320. **32-bit userspace only (`armeabi-v7a`)** — pick armeabi-v7a APKs when sideloading.

## Changes made (2026-11-01)

| Change | Command used | Undo |
|---|---|---|
| Installed Dash | `adb install -r app-release.apk` | `adb uninstall dev.jolley.dash` |
| Usage access for Recents | `adb shell appops set dev.jolley.dash GET_USAGE_STATS allow` | `… deny` |
| Disabled Google TV launcher | `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx` | `adb shell pm enable com.google.android.apps.tv.launcherx` |
| Disabled setup-recovery (it grabbed Home next, HOME priority 1) | `adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith` | `adb shell pm enable com.google.android.tungsten.setupwraith` |
| Dash as preferred Home (another launcher, **Dispatch** `com.spauldhaliwal.dispatch`, is installed) | `adb shell cmd package set-home-activity dev.jolley.dash/.MainActivity` | enable the two packages above |
| Installed SmartTube 32.68 stable (armeabi-v7a, from github.com/yuliskov/SmartTube; signer "CN=Yurii L, O=SmartTube", SHA-256 `109735aa…2eda482a`) | `adb install -r` | `adb uninstall org.smarttube.stable` |

### v0.4 (2026-11-08)

| Change | Command | Undo |
|---|---|---|
| Media access for the Now Playing widget (**applied**) | `adb shell cmd notification allow_listener dev.jolley.dash/dev.jolley.dash.data.MediaListener` | `… disallow_listener …` |
| Bluetooth devices in the Connections widget (not applied — press OK on the widget for the on-TV prompt) | `adb shell pm grant dev.jolley.dash android.permission.BLUETOOTH_CONNECT` | `adb shell pm revoke …` |

**Side effect seen:** disabling `setupwraith` dropped Wi-Fi once; reconnecting fixed it and internet was verified.
If the TV ever comes back from a reboot without network, reconnect Wi-Fi first.

## Restore everything to stock

```sh
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb uninstall dev.jolley.dash          # optional
```

## Reconnecting adb

TV: **Settings → System → Developer options → Wireless debugging**. If the Mac is still listed under
"Paired devices", just `adb connect <ip>:<port>` using the port on that screen. Otherwise
**Pair device with pairing code** → `adb pair <ip>:<pair-port> <code>` then `adb connect <ip>:<port>`
(the two ports differ; the code/port change each time the pairing dialog opens).
