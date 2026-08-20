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

The UI has three tabs behind a `NavigationRail` (`ui/App.kt`):
- **Camera** (`ui/CameraScreen.kt`) — connect/Get/Stream/Record/Save Frame, the live thermal
  image with spotmeter tap / region overlay, temperature readouts, and the temperature-history
  chart.
- **Settings** (`ui/SettingsScreen.kt`) — staged Save/Cancel editing (mirrors the Android app's
  pattern) over camera IP, palette, units, manual range, shutter sound, spotmeter/region toggle,
  temperature alerts, and — while connected — AGC/emissivity/gain mode and a basic WiFi config
  dialog (no SSID scan).
- **Library** (`ui/LibraryScreen.kt`) — browses saved `.tjsn` frames grouped by date folder,
  multi-select delete, and a full-size browse view with next/prev.

**Not yet ported** from the Android app:
- mDNS auto-discovery fallback when a camera's DHCP lease changes while disconnected (needs a
  JVM mDNS library such as JmDNS), and the "Find tCam Devices" discovery dialog in Settings
- Recording (`.mtjsn`) / time-lapse (`.tltjsn`) browsing and playback in Library (only `.tjsn`
  single frames are listed)
- Charts screen (browsing saved `.tchart` temperature-history files)
- Library's date-range filter dialog
- WiFi SSID scanning in the Settings WiFi dialog (SSID must be typed manually)
- Region measurement resize via drag (region box position isn't currently interactively
  adjustable from the UI)
- Composite image export (PNG with metadata chrome baked in), share, and shutter sound (uses a
  system beep instead of the bundled WAV)

Saved files land under `~/tCamViewer/{Pictures,Movies,Charts}/MM_DD_YYYY/`.
