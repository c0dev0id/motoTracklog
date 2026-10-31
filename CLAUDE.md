# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project scope

motoTracklog is a GPS track logger for a motorcycle navigation device, and a supporting
app of motoLauncher (`c0dev0id/motoLauncher`). It records raw fixes from the internal
GNSS chipset or a paired Bluetooth NMEA receiver in a foreground service, keeps one file
per run, and exports runs as GPX. The recording core comes from `c0dev0id/gpsLog`; the
look, the input model, the build and the distribution follow motoLauncher. It must be
operable with gloves and with a handlebar remote, not just by touch.

## Commands

These run in CI, not here — AGP is unavailable on this platform and the firewall blocks
it. They are listed because they are what a change is judged by:

```sh
# what Check runs on a branch push (one Gradle invocation)
python3 -m unittest discover -s .github/scripts     # release notes script
./gradlew --continue lintGithubDebug testGithubDebugUnitTest assembleGithubDebug

# what Build adds on main
./gradlew lintGithubRelease
./gradlew assembleGithubRelease -PappVersionName=dev-<sha> -PappVersionCode=<commit count>

# what Release builds
./gradlew assembleGithubRelease -PappVersionName=1.0.<n> -PappVersionCode=<commit count>

# one test class, or one test
./gradlew testGithubDebugUnitTest --tests "de.codevoid.mototracklog.RunRepositoryTest"
./gradlew testGithubDebugUnitTest --tests "de.codevoid.mototracklog.RunRepositoryTest.mergeKeepsTheEarlierIdAndSortsThePointsByTime"
```

One product flavor, `github` (the release-page APK, with the self-updater). A `play`
flavor is added only when asked for; it then follows motoLauncher: no updater, no
`INTERNET`, the update code gated on a `SELF_UPDATE` build-config flag.

## Conventions

`CHANGELOG.md` does not exist yet; it is created at the first release, and the Release
workflow refuses to run without an `[Unreleased]` section. Once it exists it follows
motoLauncher's rules: written for the rider, `[Unreleased]` reads as the net description
of the coming release, and entries name no classes, permissions or CI.

Read `.github/decisions.md` before proposing structural changes. Where `.github/` and
this file disagree, the code wins and the stale one gets fixed in the same task.

Components shared with motoLauncher (`EscapeKeys`, `blockKeyLongPress`, `TouchOnlyRow`,
the immersive helpers, `showOptionsDialog`, the updater, the palette, the tile and dialog
styles) are copies. A change to one belongs in every app that carries it.

## Hard UX constraints (glove + remote usage)

- **No swipe gestures on primary flows.** Scrolling exists only in the Runs list and the
  settings grid, which are touch-only, off-bike flows.
- **Large touch targets.** Every size is a `@dimen` in `values/dimens.xml` (phone) with an
  override in `values-sw600dp/dimens.xml` (the 7" 1920x1080 device), never set per view.
- **The remote reaches only Start and Pause/Resume.** On the Record screen the primary
  tile is the one focusable view. Stop sits in a `TouchOnlyRow`: a mis-press on the road
  may pause a run but never end it. The header (Runs, Settings) and the latest-run row
  are touch-only. Runs, Export and settings mode have nothing focusable: rows, tiles and
  Share GPX are unfocusable, and the two `RecyclerView`s are set unfocusable in code (their
  constructor overrides the XML attribute). Dpad traversal is the native View focus
  engine; do not add custom key handling.
- **Escape goes back.** Short or held, Escape leaves settings mode, then the screen
  (`EscapeKeys` with the same action for both). It launches nothing.
- **Orientation is a setting, Sensor by default**, applied in `onCreate` and `onResume`
  of every activity; no activity lists `orientation` in `configChanges`, so rotation
  recreates it and `layout-port` takes effect.
- **Immersive**: the status bar is always hidden, the navigation bar follows `NavBarStore`.
  Every dialog goes through `showImmersive()`.
- **Dialogs are touch-only.** Dialog rows and `AlertDialog` buttons are unfocusable
  (`DialogAction`, `DialogButton`); Escape cancels.

## Architecture

Kotlin, classic Views, AppCompat + Material3, view binding, coroutines. No Compose, no
database, no DI, no networking library. Versions are in `.github/stackinfo.md`.

Package layout under `de.codevoid.mototracklog`:

- `RecordActivity` — launcher entry (`singleTask`, so the notification returns to it).
  Status word from the pure `recordStatus()`, a subtitle composed from string resources,
  six stat tiles while a run is active and the latest run while idle, and the action row,
  which is a row of the same tile grid (level with the bottom stat row in landscape, below
  the stats in portrait).
  Collects `LoggingStateHolder.state` and `RecordViewModel.latestRun` while started, and
  sets `LoggingStateHolder.uiVisible` in `onResume`/`onPause`. The idle status needs
  three platform facts (`hasInternalGps`, Location switched on, precise location); they
  are re-read on resume and from a `PROVIDERS_CHANGED_ACTION` receiver registered for the
  started lifetime, because the Location switch is flipped from the shade without pausing
  the screen. The Start tile is enabled exactly when the status is `Idle`. On resume and when
  Location is switched on, a run whose marker is set but whose service is not running is
  resumed (`resumeInterruptedRun`), covering a reboot or restart the platform refused in the
  background; the primary tile reads Resume while such a run is pending. Settings mode
  swaps the body for a `TileAdapter` grid (GPS source, units, debug log, share debug log,
  theme, orientation, navigation bar, permissions, update check); `settingsTilesCache` is
  cleared by every tile that changes state and on resume. The GPS source tile is locked
  while a run is active. Permissions are asked once, up front, when precise location is
  missing: location (+ notifications on 13+), then background location (10+), then the
  battery-optimization exemption. Bluetooth access is asked from the source picker.
- `RunsActivity` — `ListRowAdapter` list, newest first. Tap opens Export; a touch long
  press opens `showOptionsDialog` with Delete (confirmed) and Merge with previous run
  (the row below). The active run (`ActiveRunStore.runId`) has no menu.
- `ExportActivity` — one run. Three filter tiles open preset pickers and write
  `ExportFilterStore`; `ExportViewModel` recomputes the kept-point count on IO, cancelling
  the previous computation, and re-reads the file each time instead of caching points.
  Share GPX writes `cacheDir/exports/motoTracklog-<start>.gpx` and opens the share sheet.
- `service/LoggingService` — foreground service of type `location`, `START_STICKY`,
  driven by `ACTION_START/RESUME/PAUSE/UNPAUSE/STOP`; a Start while a run's marker is set
  continues that run, so only Stop ends one. All run state lives on the
  `gps-logger` `HandlerThread`. Pause is cold (the source is released and the flag is
  persisted). A platform refusal (`startForeground`, a missing permission, no chipset,
  Bluetooth off, an internal-GPS resume with no screen visible and no background location on
  11+) is caught and never crashes; only a run that never began gives up its marker. No wake lock, no `fdatasync`. UI state updates are throttled to 2 s and skipped
  while no screen is visible; the notification carries Pause/Resume and Stop. Android 9
  paths: `ServiceCompat`, `LocationListenerCompat` (abstract provider callbacks before 11),
  `LocationManagerCompat` for `hasProvider` and GNSS status, and the NMEA listener's
  Handler overload below 11.
- `service/BluetoothNmeaSource` — RFCOMM on the SPP UUID, read on its own thread, each fix
  posted back to `gps-logger`. Reconnect backoff 3 s → 60 s, cut short by
  `ACTION_ACL_CONNECTED`. `service/BootReceiver` resumes an active run after a reboot.
- `data/` — `RunCodec` (16-byte header, fixed 68-byte big-endian records), `RunWriter` /
  `RunReader`, `RunRepository` (`filesDir/runs/<startMillis>.dat`; merge writes a temp file
  and renames it over the earliest id), `NmeaParser` (RMC emits, GGA enriches,
  accuracy = HDOP × 5 m), and one `SharedPreferences` store per concern: `ActiveRunStore`
  (file `recording`), and `SourceStore`, `DebugLogStore`, `ExportFilterStore`,
  `UnitsStore`, `ThemeStore`, `OrientationStore`, `NavBarStore` (file `settings`).
- `export/` — `PointFilter` (accuracy first, then distance or time; distance beats time)
  and `GpxExporter` (`android.util.Xml`, a new `<trkseg>` after a gap over 60 s).
- `ui/` — shared components (`KeyInput`, `Immersive`, `TouchOnlyRow`, `OptionsDialog`,
  `TileAdapter`, `ListRowAdapter`, `UpdateFlow`), and the pure helpers `Format.kt` and
  `RecordStatus.kt`, which stay free of Android imports.
- `update/UpdateChecker` — motoLauncher's: a release build follows the latest release, a
  `dev-` build the `dev` pre-release; the asset name `motoTracklog-<versionName>.apk`
  carries the version.

## Resources & styling

- Colour tokens in `values/colors.xml` with night overrides in `values-night/colors.xml`;
  layouts reference tokens, never literals. `alert` is this app's addition, for statuses
  that stop or starve a run.
- A dotted style name meant as a root sets `parent=""`, or AAPT fails in CI.
- Michroma (`res/font/michroma.ttf`, OFL) for the status word, stat values, action tiles
  and screen titles.

## Build & CI

**Do not attempt to build locally.** All builds run in CI. Three workflows, as in
motoLauncher: `Check` on every branch push except `main` (release-script tests, one
Gradle invocation for lint, unit tests and the debug APK, uploaded as `app-debug`);
`Build` on push to `main` (lint of the release variant, tests, signed
`assembleGithubRelease`, the `dev` pre-release recreated with
`motoTracklog-dev-<sha>.apk`, and a separate `dist` job without `GITHUB_TOKEN`
permissions that pushes the same file to the home.codevoid.de dist store with
`?version=dev-<sha>`; `DIST_TOKEN`, which selects the dist repository, reaches only that
step); `Release` by hand from `main` (next `1.0.x`, changelog
section as notes, atomic push of commit and tag, GitHub release). `versionCode` is the
commit count of `main`. Signing comes from the `SIGNING_KEYSTORE_*` / `SIGNING_KEY_*`
secrets; never commit signing material.

Tests are JUnit4 + Robolectric in `app/src/test/kotlin`, with Android resources. Write new
behaviour so it lands in a pure function, a store, or something Robolectric can inflate.
No device is available to check it.
