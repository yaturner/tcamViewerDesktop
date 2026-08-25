# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Linux/desktop viewer for the [tCam](https://github.com/danjulio/tCam) thermal imaging camera — a
Compose Multiplatform Desktop port of [tcamViewer2](https://github.com/yaturner/tcamViewer2) (the
Android app). Connects to a tCam over WiFi/TCP (port 5001), decodes its JSON-framed radiometric +
telemetry frames, applies a color palette, and displays live thermal video with spotmeter/region
temperature readout.

This codebase is deliberately kept in lockstep with tcamViewer2: most classes are line-for-line
ports (same package-relative structure, same method names) with only the Android-specific plumbing
swapped for JVM/desktop equivalents. When porting a feature from tcamViewer2, mirror its existing
class/method naming rather than inventing new patterns — see "Porting from tcamViewer2" below.

## Build Commands

```bash
# Build and run the app
./gradlew run

# Full build
./gradlew build

# Run unit tests
./gradlew test

# Package a native installer
./gradlew packageDeb
./gradlew packageAppImage
```

There is no linter/formatter task configured (no Spotless/ktlint), and no test sources exist yet.

## Architecture

### Camera Protocol

Same wire protocol as tcamViewer2: JSON messages framed with STX (``) prefix and ETX
(``) suffix over TCP port 5001, carrying base64-encoded `radiometric` (16-bit LE pixels,
160×120) and `telemetry` (16-bit LE words at fixed offsets A=0/B=80/C=160) plus a `metadata` block.
Commands split into two patterns in `CameraService`:
- Fire-and-forget (`getImage()`, `runFfc()`, `setConfig()`, `setWifi()`) — a plain `writeCommand()`
  call inside `serviceScope.launch { }`, no response is awaited.
- Request/response (`getConfig()`, `getWifi()`, `startStreaming()`/`stopStreaming()`,
  `setSpotmeter()`) — `sendCmd()` registers a `CompletableDeferred` in `pendingRequests` keyed by
  the expected response field, then `withTimeout` awaits it; the socket-reading loop
  (`startListening()`) resolves it via `routeToPendingRequest()` when a framed response arrives
  whose top-level `cmd`/`type` or a matching key shows up.

Streamed frames that aren't claimed by a pending request are pushed onto `imageChannel`, an RxJava
`PublishSubject<JSONObject>` exposed via `getConnectionLostSignal()`/`getImageChannel()` as hidden
`Observable`s.

### Data Flow

```
CameraService (TCP socket, java.net.Socket + coroutine read loop)
    │
    ├─ sendCmd() / pendingRequests map ──► one-shot request/response commands
    │
    └─ imageChannel (RxJava PublishSubject) ──► continuous frame stream
                                                     │
                                               CameraViewModel
                                                     │
                                               CameraScreen (Compose)
```

### Global Singletons

`Globals.kt` declares file-level `lateinit var`s, initialized once in `Main.kt`'s `main()` before
the Compose `Window` is created — this mirrors tcamViewer2's `MainActivity.kt` global-singleton
pattern exactly (no Android `Service`/`Application` lifecycle to hang them off of here):

```kotlin
lateinit var cameraService: CameraService
lateinit var cameraUtils: CameraUtils
lateinit var paletteFactory: PaletteFactory
lateinit var settingsManager: SettingsManager
```

### Key Classes

| Class | Role |
|-------|------|
| `net/CameraService.kt` | Owns the TCP socket and all I/O. Same `sendCmd()`/`pendingRequests`/`imageChannel` design as tcamViewer2's `CameraService`, minus the `android.app.Service` wrapper (no Binder/Intent needed). |
| `model/CameraViewModel.kt` | Full app state as `StateFlow`s (connection, spot/max/min/region temps, histogram, streaming/recording/time-lapse, temperature history, alerts) — ported logic-for-logic from the Android view model. |
| `util/CameraUtils.kt` | Image processing: decodes base64 radiometric/telemetry, maps pixel values through the active palette to an `IntArray` of ARGB pixels. |
| `factory/PaletteFactory.kt` + `palette/*.kt` | Same 10 color palettes as tcamViewer2 (Arctic, Banded, Blackhot, DoubleRainbow, Fusion, Gray, Ironblack, Isotherm, Rainbow, Sepia). |
| `util/SettingsManager.kt` | Desktop stand-in for tcamViewer2's DataStore-backed `SettingsDataManager` — same key set and `Flow`-based read API, backed by `java.util.prefs.Preferences` instead of DataStore. |
| `net/CameraDiscovery.kt` | mDNS discovery via [JmDNS](https://github.com/jmdns/jmdns) (the desktop JVM has no built-in mDNS client like Android's `NsdManager`). Binds a separate JmDNS instance to every active non-loopback IPv4 interface and merges results, since a plain `JmDNS.create()` can silently miss cameras on a multi-homed machine. Also used by `CameraViewModel`'s auto-reconnect as a fallback scan after a few failed retries at the last-known IP. |
| `util/WifiScanner.kt` | Shells out to `nmcli` for WiFi SSID scanning (no portable desktop WiFi-scan API like Android's `WifiManager`). |
| `util/CompositeExport.kt` | Builds the colorized image + header/sidebar/footer chrome (color bar, spotmeter arrow, max/min, gain/emissivity/date-time), ported from Android's `buildShareBitmap()`; saves to `~/tCamViewer/Exports/` instead of sharing via `Intent.ACTION_SEND`. |

### UI Structure

`ui/App.kt` hosts a `NavigationRail` (not tcamViewer2's `ModalNavigationDrawer`) with four tabs:
- `ui/CameraScreen.kt` — connect/Get/Stream/Record/Save buttons, an FFC button (`run_ffc`), live
  thermal image with spotmeter tap-to-move / region drag-to-move-or-resize overlay, temperature
  readouts, temperature-history chart.
- `ui/SettingsScreen.kt` — staged Save/Cancel editing (same pattern as tcamViewer2's Settings tab)
  over camera IP (with mDNS "Find tCam Devices"), palette, units, manual range, shutter sound,
  spotmeter/region toggle, temperature alerts, and — while connected — AGC/emissivity/gain mode
  and a WiFi config dialog with `nmcli` SSID scanning.
- `ui/LibraryScreen.kt` — browses saved `.tjsn`/`.mtjsn`/`.tltjsn` files grouped by date, multi-
  select delete, date-range filter, full-size browse with Export-to-PNG, and frame-by-frame video
  playback (`ui/VideoPlayerWindow.kt`) with skip ±5, play/pause, and speed control.
- `ui/ChartsScreen.kt` — browses saved `.tchart` files the same way, with a full chart view.

Saved files land under `~/tCamViewer/{Pictures,Movies,Charts,Exports}/MM_DD_YYYY/`.

### Porting from tcamViewer2

When bringing a feature over from the Android app, follow the pattern established for existing
ports (e.g. FFC): add the protocol constant to `constants/Constants.kt`, a `CameraService` method,
a thin `CameraViewModel` wrapper, then wire it into the relevant Compose screen — reusing the exact
command string/JSON shape from tcamViewer2's `Constants.kt` rather than re-deriving it. Only the
platform plumbing changes (RxJava/coroutines stay the same; Android-only APIs like `NsdManager`,
`WifiManager`, DataStore, and `Intent.ACTION_SEND` get JVM-desktop substitutes as listed above).

### Not Yet Ported from tcamViewer2

- MP4 export/share for recordings and time lapses (Android's `VideoExporter` uses `MediaCodec`; no
  portable desktop video encoder wired up)
- Fullscreen toggle in the video player
