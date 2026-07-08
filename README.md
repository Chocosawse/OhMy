# OhMy — Nikon Zf Wi-Fi Sync

A lightweight Jetpack Compose Android app that connects directly to a Nikon Zf's built-in Wi-Fi
access point and pulls new JPEGs to your phone as they're shot, with no account, login, or cloud
service involved.

## How it works

1. On the camera: **Menu → Network → Wi-Fi → On**. The camera shows an SSID and password on its
   screen and starts acting as its own Wi-Fi access point.
2. In the app: enter that SSID/password and tap **Connect**. The phone joins the camera's network
   directly (via `WifiNetworkSpecifier`), without changing the phone's default Wi-Fi network.
3. The app opens a [PTP/IP](https://en.wikipedia.org/wiki/Picture_Transfer_Protocol) (ISO 15740)
   session with the camera — the same tethering protocol used by Nikon's Wireless Transmitter
   Utility / Camera Control Pro — and listens for `ObjectAdded` events, which the camera fires the
   moment a new photo is written to the card.
4. New JPEGs are downloaded and saved straight to `Pictures/OhMy` on the phone via MediaStore. RAW
   (NEF) files are skipped so syncing stays fast; a manual **Sync now** button is also available to
   catch anything missed.
5. A foreground service keeps the connection and event listener alive while the app is
   backgrounded (screen off), stopping automatically on disconnect.

## Project layout

```
app/src/main/java/com/ohmy/zfsync/
  ptpip/     PTP/IP wire protocol: packet framing, PTP data types, PtpIpClient
  network/   CameraWifiManager — joins the camera's AP via WifiNetworkSpecifier
  sync/      CameraSyncRepository — connection lifecycle, event handling, download orchestration
  storage/   PhotoSaver — MediaStore writes (scoped storage, no legacy storage permission)
  service/   CameraSyncService — foreground service for background auto-sync
  ui/        Compose screens (Connect, Sync), ViewModel, theme
```

## Building

Open the project in Android Studio (Koala/Ladybug or newer) and run the `app` module. It targets
`minSdk 29` (Android 10+) since it relies on `WifiNetworkSpecifier`, which isn't available on
older releases without much more invasive (and less reliable) legacy Wi-Fi APIs.

On Android 13+ the app requests `NEARBY_WIFI_DEVICES` (needed to join a Wi-Fi network by SSID
without location permission) and `POST_NOTIFICATIONS` (for the sync service's status
notification) at first launch.

## Known limitations / what to verify on real hardware

This was built and reviewed without access to a physical Nikon Zf, so the PTP/IP implementation
follows the published ISO 15740 spec and Nikon's known vendor conventions as closely as possible,
but the following should be the first things you check against your actual camera:

- **Filename/format filtering**: JPEGs are recognized both by PTP object format code (`0x3801`)
  and by file extension, to be tolerant of how the Zf reports RAW+JPEG pairs.
- **Event delivery timing**: `ObjectAdded` should fire within a second or two of the shutter, but
  buffered/burst shooting behavior on the camera side hasn't been observed directly.
- **Wi-Fi mode**: this targets the camera's own Wi-Fi access point (the "Connect to smart device"
  Wi-Fi mode), not SnapBridge's separate Bluetooth-paired auto-transfer flow.

If handshake or session setup fails against your unit, the connection screen surfaces the raw
error message, which should make it straightforward to narrow down the mismatch.
