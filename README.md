# tcamViewerDesktop

Linux/desktop viewer for the [tCam](https://github.com/danjulio/tCam) thermal imaging camera —
a Compose Multiplatform Desktop port of [tcamViewer2](https://github.com/yaturner/tcamViewer2)
(the Android app).

Connects to a tCam over WiFi/TCP (port 5001), decodes its JSON-framed radiometric + telemetry
frames, applies a color palette, and displays live thermal video with spotmeter/region
temperature readout.

## Build & run

```bash
./gradlew run
```

## Package a native installer (.deb / AppImage)

```bash
./gradlew packageDeb
./gradlew packageAppImage
```

## Status

Ported from tcamViewer2's Android source: the TCP protocol layer (`net/CameraService.kt`),
radiometric/telemetry decode + palette mapping (`util/CameraUtils.kt`, `model/ImageDto.kt`),
and the full view-model (`model/CameraViewModel.kt`) — connect/disconnect with auto-reconnect,
live streaming, point spotmeter + region measurement, AGC/manual-range/palette/units control,
temperature-over-time history, temperature alerts, single-frame save, recording (`.mtjsn`), and
time-lapse capture (`.tltjsn`).

**Not yet ported** from the Android app:
- mDNS auto-discovery fallback when a camera's DHCP lease changes while disconnected (needs a
  JVM mDNS library such as JmDNS)
- Library screen (browsing/deleting saved `.tjsn`/`.mtjsn`/`.tltjsn` files, video playback)
- Charts screen (browsing saved `.tchart` temperature-history files)
- WiFi configuration UI (the underlying `sendWifiConfig()` call is ported, no UI wired to it yet)
- Region measurement resize via drag (region box position isn't currently interactively
  adjustable from the UI)
- Composite image export (PNG with metadata chrome baked in) and shutter sound (uses a system
  beep instead of the bundled WAV)

Saved files land under `~/tCamViewer/{Pictures,Movies,Charts}/MM_DD_YYYY/`.
