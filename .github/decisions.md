# Decisions

Key decisions and the reasons behind them. Decisions carried over from gpsLog and
motoLauncher keep their reasoning here so it does not have to be looked up in another
repository.

## Origin

### A motoLauncher supporting app built on gpsLog's recording core
gpsLog already recorded and exported reliably but was a Compose app for phones. The
recording core (service, sources, file format, filter, GPX) is ported nearly unchanged;
the screens are rebuilt in classic Views on motoLauncher's design and input model.

### Shared components are copies
Each supporting app has its own repository. `EscapeKeys`, `blockKeyLongPress`,
`TouchOnlyRow`, the immersive helpers, `showOptionsDialog`, the updater, the palette and
the tile and dialog styles are copied from motoLauncher rather than published as a
library. A change to one of them is made in every app that carries it. motoLauncher's
`AppTileAdapter` became `TileAdapter` here, without the icon, dimming and long-press
this app's tiles never use.

## Platform

### Android 9 is the floor
gpsLog required Android 14 to avoid every compatibility path. The supporting apps target
phones from Android 9, so the compat paths are back, all in the service and the source:
`ServiceCompat.startForeground` (the typed overload is API 29), `LocationListenerCompat`
(before Android 11 the provider and status callbacks are abstract and a partial listener
throws `AbstractMethodError`), `LocationManagerCompat.hasProvider` (API 31) and its GNSS
status registration (the Executor overload is API 30), the NMEA listener's Handler
overload below Android 11, `ContextCompat.registerReceiver` and
`IntentCompat.getParcelableExtra`. Bluetooth access is `BLUETOOTH` (install-time) up to
Android 11 and the runtime `BLUETOOTH_CONNECT` from Android 12; `hasBluetoothAccess()`
answers both. Background location exists from Android 10, notification permission from
Android 13.

### Platform `LocationManager`, not the fused provider
The fused provider needs Play Services, caps at about 1 Hz and smooths fixes.
`requestLocationUpdates(GPS_PROVIDER, 0, 0f)` delivers the chipset's native rate.

### No database, no DI, no networking library
A run is one append-only file; settings are one small `SharedPreferences` store per
concern, as in motoLauncher. GPX uses `android.util.Xml`, the updater
`HttpURLConnection` and `org.json`.

## Remote and glove input

### The remote reaches Start and Pause/Resume only
Pausing while riding is useful and harmless: the same tile resumes. Stopping is not: a
mis-press would end the recording until someone notices. Stop sits in a `TouchOnlyRow`;
ending a run happens parked, by touch, or from the notification. The primary tile is one
view whose text follows the state, so remote focus never moves when the state changes.

### Escape goes back, held or not
motoLauncher's held Escape launches the navigation app, but that setting lives in
motoLauncher's private preferences. A supporting app could only duplicate it or ask
motoLauncher through a new contract; neither was wanted. A held Escape therefore does
what a short one does. A gloved press is not timed precisely, and a hold that did
nothing would read as a dead key.

### Everything that needs touch to leave is unreachable by dpad
The Record header (Runs, Settings) and the latest-run row are in `TouchOnlyRow`s. Runs,
Export and settings mode are entered only by touch. Once there, tiles and rows are
focusable as in motoLauncher's settings mode, and Escape leaves.

### `TouchOnlyRow` closes every path to focus that is not a touch
The platform gives focus without a touch in three ways: dpad and Tab search
(`addFocusables`), default focus at window start, on leaving touch mode and after the
focused view goes away (`onRequestFocusInDescendants`), and the offer of a child that
becomes focusable while nothing has focus (`focusableViewAvailable`). The row overrides
all three. Closing only the first left the header reachable: after any touch, the next
remote key restored default focus to the first focusable view in tree order, the Runs
button. `FOCUS_BLOCK_DESCENDANTS` would close all three in one line but also refuse a
child's own `requestFocus()`, which motoLauncher's search field makes when tapped.

### Permissions are asked once, up front, when precise location is missing
Location (with notifications on Android 13+), then background location (Android 10+),
then the battery-optimization exemption. Background location is what lets a reboot or a
sticky restart start the location service from the background. Asking only when
precise location is missing keeps a rider who declined background location from being
sent to the settings page on every launch. The Permissions settings tile opens the app's
system settings for anything declined. Bluetooth access is asked from the GPS source
picker, so an internal-only rider is never prompted.

## Record screen

### The Start tile is gated on the status word
`recordStatus()` ranks the idle obstacles outermost first: no chipset, Location off, no
precise location. Start is enabled exactly when the status is Ready, so the word and the
tile cannot disagree. Location is usually switched from the shade, which does not pause
the screen, so a `PROVIDERS_CHANGED_ACTION` receiver re-reads it while the screen is
started.

### "Recording" instead of "Fix"
gpsLog named the state after the receiver. On a handlebar the question is whether the
ride is being recorded; Searching and Receiver off already name the receiver's trouble.

### Status colours
The accent while points arrive, muted while paused, and a new `alert` token for anything
that stops or starves a run. motoLauncher's palette had no colour for a fault.

### Six live values while recording, the latest run while idle
Satellites, accuracy, points, rate, speed and GPS time, in display-only stat tiles. Idle,
the same slot shows the run just recorded, which leads to its Export page. Speed is shown
in km/h or mph (a Units setting), not gpsLog's m/s; the file keeps m/s.

### The subtitle needs no ticking clock
The recorded span is last fix minus start: it advances with the fixes and freezes while
paused.

### Settings mode, not a settings screen
As in motoLauncher: a header toggle swaps the body for settings tiles. The GPS source
tile is locked while a run is active, because the service reads the source only when a
run starts or resumes.

## Runs and export

### Runs are a list, not a tile grid
A run has no icon; it is identified by its date and described by duration and point
count. A grid truncates that and wraps a chronology left to right. Full-width rows read
top to bottom, newest first. `item_list_row` is a new shared component for any list of
text records.

### A per-run long-press menu instead of a selection mode
Tap opens Export, a touch long press offers Delete and Merge with previous run, through
`showOptionsDialog`. The common merge is rejoining a ride split by a stop, and "previous"
is the row right below, visible while choosing. gpsLog's selection mode with multi-run
export was dropped. Delete is confirmed; it is the one irreversible action. The run
being recorded has no menu, because its file is still being written.

### Filters from presets
Accuracy (5–100 m), minimum distance (Off–50 m) and interval (Off–60 s) are picked from
lists, not typed. No keyboard and no parsing on a gloved screen. The interval tile says
when it is ignored: while a distance is set, distance alone decides (`PointFilter`).

### Filters act only at export
Recording keeps every raw fix. The preview re-reads the run on each filter change and
cancels the computation in flight, rather than holding the points in memory.

### GPX files are named after the run's start
`motoTracklog-<yyyyMMdd-HHmm>.gpx`, in local time, so shares from different runs do not
collide and the name says which ride it is.

## Recording (carried over from gpsLog)

### One thread owns a run
All run state lives on the `gps-logger` HandlerThread; both sources deliver there through
`FixSink`. The Bluetooth socket is read on its own thread, so a blocking read never
starves the flush and notification callbacks.

### Pause is cold and persisted
Pausing releases the chipset or the Bluetooth link, so a paused run draws no power. The
flag is stored next to the active run id, so a restart comes back paused. A Resume that
cannot re-acquire the source leaves the run paused rather than discarding it.

### A platform refusal never crashes, and only a run that never began loses its marker
`startForeground` with the location type throws without a location permission, and from
Android 14 a background start also needs background location. On Android 11 to 13 that
start succeeds, but the internal GPS delivers no fixes to it, so the service refuses such
a resume itself and the run waits for the Record screen, as it does on 14. A missing
chipset or Bluetooth that is off refuses the source. Each refusal is caught. At `BOOT_COMPLETED` Bluetooth is often not
up yet, so clearing the marker there would end a run the rider never stopped.

### An interrupted run resumes when the app comes to the front
Without background location, neither the boot receiver nor a sticky restart may start
the location service, and the run's marker stays behind. gpsLog then showed Ready, and a
Start began a second run over the marker. The Record screen now resumes the pending run
whenever nothing blocks a start: on resume, and when Location is switched on. The service
guarantees the rest: a Start while a marker is set continues that run, whoever sends it,
so only Stop ends a run. The primary tile reads Resume in that state, because that is
what it does.

### No wake lock, no `fdatasync`
The location service type keeps the subsystems running. A process kill loses nothing
(the page cache survives); a power loss loses at most the 60 s flush interval.

### UI updates are throttled and skipped in the background
State updates at most every 2 s, and none while no screen is visible. The notification
is updated regardless.

### Fixed-size binary records
A 16-byte header and 68-byte records with a presence bitmask: point count and random
access are arithmetic on the file length, and a torn trailing record is ignored. There is
no end marker, so a stop and a crash produce the same file. Pre-1.0 there is no migration:
a format change bumps the version and old files no longer read.

### Merge writes a temporary file first
The merged points are written to `<id>.dat.tmp`, then the originals are deleted and the
temporary file is renamed over the earliest run's id, so a crash mid-merge loses nothing.

### NMEA accuracy is HDOP × 5 m
NMEA has no accuracy in metres, and the export filter drops points without one.

### Bluetooth reconnects back off
3 s doubling to 60 s, cut short by `ACTION_ACL_CONNECTED` for the paired address.
Receivers that never re-initiate a link are covered by the backoff alone.

The ACL receiver is registered as exported. Below Android 13, `ContextCompat` implements
`RECEIVER_NOT_EXPORTED` with a signature permission of the app's own, which only the
system UID bypasses; the Bluetooth stack sends this broadcast from its own UID, so it was
dropped. `ACTION_ACL_CONNECTED` is a protected broadcast that no app can send, so
exporting the receiver opens nothing. The Location receiver on the Record screen stays
unexported: that broadcast comes from the system UID.

### Bluetooth devices are not filtered
Bluetooth has no class for a GNSS receiver; every paired classic or dual-mode device is
offered and the rider picks.

## Distribution

### One github flavor; play on demand
The updater lives in `main`; the github source set adds `INTERNET` and
`REQUEST_INSTALL_PACKAGES`. A play flavor brings back motoLauncher's `SELF_UPDATE` flag
when it is added.

### Update channels, version code and releases follow motoLauncher
A release build queries the latest release, a `dev-` build the `dev` pre-release; the
asset name carries the version. `versionCode` is the commit count of `main`. Releases are
cut by hand from `main` and pushed atomically after the build succeeds.

## Build and CI

### Check, Build and Release as in motoLauncher
`Check` lints, tests and builds the debug APK on every branch push in one Gradle
invocation. `Build` republishes `dev` on push to `main`. `Release` is the only producer
of a versioned release. The debug build has a `.debug` application ID, so a branch can be
tested beside the installed release, recording into its own storage.
