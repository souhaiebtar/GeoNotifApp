# GeoNotif

A native Kotlin Android app that alerts you when you approach a saved destination — designed for resting on public transport.

## Features

- Material Design 3 UI with automatic system light/dark theme, a journey dashboard, destination cards, and matching map controls.
- Multiple destinations, stored locally across app restarts.
- Per-destination enable/disable, name, latitude/longitude, and arrival radius (100–10,000 m).
- Choose a system ringtone/alarm sound (or Silent) independently for each destination.
- Choose Off, Gentle, or Strong vibration independently for each destination.
- **Add destination** popup with **Save current location** and **Choose other location** buttons.
- Interactive OpenStreetMap picker: pan, pinch to zoom, tap to place a pin, and confirm the selected coordinates.
- Current-location capture with precise permission handling, a cancellable loading dialog, and timeout/retry.
- Foreground GPS/network location monitoring with a persistent notification and distance/accuracy status.
- Arrival notification plus repeating alarm audio/vibration, automatically stopped after 60 seconds.
- Dismiss the alarm or stop monitoring from notifications or the main screen.
- No account, app backend, advertising, API key, or Google Play Services dependency. Internet is used for map tiles; monitoring remains local.

## Build and run

Requirements: Android Studio with Android SDK 35 and Build Tools, and JDK 17 or 21. Devices must run Android 9 (API 28) or newer.

1. Open this directory in Android Studio and let Gradle sync.
2. Install Android SDK Platform 35 through SDK Manager if needed.
3. Run the `app` configuration on an emulator or physical device.

Command-line build (set `ANDROID_HOME` to your installed Android SDK, or set `sdk.dir` in `local.properties`):

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

The Gradle 8.9 wrapper is included with distribution checksum verification.

**Build validation:** `:app:assembleDebug` and `:app:testDebugUnitTest` passed using the Android Studio SDK at `/home/unknown/Android/Sdk`. All 3 unit tests passed. `:app:lintDebug` also passed after the Material 3 / DayNight redesign (0 errors). `python3 scripts/check_theme.py` verifies paired theme tokens and 20 text-contrast combinations at ≥4.5:1. The redesigned dashboard and Add destination dialog were visually checked on the connected phone in light/dark mode. The map selection flow, live theme-switch draft restoration, and screen-off location/audio/vibration still need complete on-device acceptance testing.

## Usage

1. Tap **Add destination**. Choose **Save current location** to capture your position, or **Choose other location** to open the map. On the map, pan/zoom, tap your stop, then tap **Use this location**. Neither path saves a destination until you complete the editor and tap **Save**.
2. Set a radius; 500–1,000 m is a reasonable starting point for a bus or train.
3. Choose a sound and vibration pattern, then save. Repeat for additional stops.
4. Tap **Start monitoring** while the app is open. Grant **precise** location and notification permissions.
5. Leave monitoring running while traveling. You may turn the screen off.
6. Dismiss the arrival alarm when it fires. Stop monitoring at the end of your trip.

Each enabled destination alerts **once per monitoring session**, including immediately if the first valid fix is already inside its radius. Dismissing an alarm does not re-arm it. Stop and start monitoring to re-arm all destinations. Edits to destinations are read live, but an already-alerted destination remains disarmed until a new session.

If several destination circles are entered on the same update, one notification lists all of them and uses the first saved destination's feedback. A later arrival replaces any currently playing feedback. Unavailable sound URIs fall back to the default system alarm.

## Reliability and privacy

- Location requests are made approximately every 3 seconds; this is not a guarantee of update frequency. Fixes older than 30 seconds or with reported accuracy worse than the configured radius are ignored.
- GPS may be unavailable underground or inside vehicles. Larger radii can help, but arrival alerts are **not guaranteed** and should not be your only safeguard for an important stop.
- Sound uses the device's **alarm volume**. The app does not override volume or Do Not Disturb. Test your chosen sound on your device before relying on it.
- Some device manufacturers restrict foreground services during battery saving. If necessary, allow unrestricted battery use through Android app settings.
- Monitoring must be started by the user while the activity is visible. A location foreground service keeps monitoring when the activity goes into the background; the app does not request all-the-time background location permission.
- Monitoring does not automatically restart after a force-stop, reboot, or process termination. Check the persistent notification to confirm it is still active.
- Destination coordinates are stored only in private app preferences. Device backup is disabled and no location history is retained.
- Map imagery comes from OpenStreetMap's public tile service via osmdroid 6.1.20. Tile requests reveal your IP address and viewed map area to that provider; saved destinations and monitoring updates are not uploaded. No address search/geocoding is included.

## Material Design 3 and system theme

The app uses `Theme.Material3.DayNight.NoActionBar` with AndroidX AppCompat and Google's Material Components for Android (native Views, not a web UI). Components include Material cards, switches, filled/tonal buttons, dialogs, outlined text fields, an exposed vibration dropdown, and a map toolbar.

The phone's system light/dark setting selects matching semantic color tokens in `res/values/colors.xml` and `res/values-night/colors.xml`. No separate in-app theme override is applied. Surfaces, text, controls, dialogs, and system-bar icons adapt automatically; the branded journey panel intentionally remains dark in both modes. Map tiles use osmdroid's local night-color filter in dark mode (same OSM provider and cache, no extra requests). The destination editor preserves unfinished fields on activity recreation, including theme changes and rotation.

```sh
python3 scripts/check_theme.py
```

This checks palette parity, the DayNight parent, and text contrast. It does not replace screen-reader, font-scaling, or on-device visual testing.

## Map provider

OpenStreetMap was chosen for its free, keyless map data and street detail. The native osmdroid renderer supports gestures, markers, and a disk tile cache without Google Play Services. Attribution (`© OpenStreetMap contributors`) remains visible and links to the [OSM copyright page](https://www.openstreetmap.org/copyright).

Tiles use HTTPS, an app-specific User-Agent, and the renderer's HTTP-aware cache. There is no bulk download or offline prefetch. Uncached areas require internet; already cached tiles may remain usable offline. Public OSM tiles are community-funded and have no availability SLA or unlimited-usage promise. Follow the [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) and move to a suitable hosted/self-hosted tile provider before high-volume deployment. osmdroid is archived upstream; reassess the renderer for long-term production maintenance.

## Project layout

- `app/src/main/java/com/geonotif/app/AppUi.kt`: shared Material components, theme-token palette, and decorative route artwork.
- `app/src/main/java/com/geonotif/app/MainActivity.kt`: journey dashboard, destination editor, permission flow.
- `app/src/main/java/com/geonotif/app/MapPickerActivity.kt`: OpenStreetMap display, pin selection, attribution, and rotation-state preservation.
- `app/src/main/java/com/geonotif/app/Destination.kt`: destination model, JSON persistence, arrival validation rules.
- `app/src/main/java/com/geonotif/app/LocationMonitorService.kt`: foreground location tracking, per-session triggering, notifications, audio, vibration.
- `app/src/test/java/com/geonotif/app/ArrivalRulesTest.kt`: coordinate, boundary, freshness, and accuracy checks.

## On-device acceptance checks

1. Add two destinations with different sounds, vibration patterns, and radii; restart the app and verify persistence.
2. Start outside a radius, then use emulator Extended Controls → Location to move inside it. Verify notification, sound, and vibration (vibration requires a physical device).
3. Dismiss the alarm and send repeated inside-radius fixes; verify it does not retrigger until monitoring is restarted.
4. Confirm a disabled destination never alerts and overlapping arrivals are listed together.
5. Test with the screen off and app in the background on a real device.
6. Test silent/vibration-only destinations, notification denial, approximate-only location, location switched off, Do Not Disturb, and battery saver.
7. Verify both the 60-second timeout and Stop monitoring terminate audio/vibration.
8. Verify Add destination opens the two-choice popup. Test current-position success, denied permission, GPS off, timeout, retry, and cancellation; none should silently create a destination.
9. Choose another location without location permission; pan/zoom, tap to move the pin, rotate, confirm, and verify the editor coordinates match. Save and verify persistence. Cancel either screen and verify nothing is added.
10. Test map rendering online and with no connection; confirm the offline guidance and OSM attribution remain visible.
11. Switch the system between light and dark while on the dashboard, map, and an unfinished destination form. Check contrast, system bars, map selection, and preservation of typed form values. Repeat with large system fonts and landscape orientation.
