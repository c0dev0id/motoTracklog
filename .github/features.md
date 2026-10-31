# Features

- **Recording.** A foreground service records every fix at the receiver's native rate,
  with all fields the fix carries, into one append-only file per run. The notification
  shows points and rate and offers Pause/Resume and Stop.
- **Recording sources.** The internal GNSS chipset, or a paired classic-Bluetooth NMEA
  receiver over SPP, chosen in settings mode. The link reconnects with backoff when it
  drops. A device without a chipset records from a receiver.
- **Remote operation.** The handlebar remote reaches the Start / Pause / Resume tile on
  the Record screen and nothing else. Escape, short or held, leaves settings mode, then
  the screen.
- **Record screen.** A status word (Ready, Recording, Searching, Paused, Receiver off, GPS
  disabled, Unavailable, No GPS device) with a subtitle naming the cause or the recorded
  span; six live values (satellites, accuracy, points, rate, speed, GPS time) while
  recording; the latest run while idle.
- **Cold pause.** Pausing releases the receiver; the paused state survives a restart.
- **Resume after interruption.** A process kill or a reboot continues the same run until
  Stop; where Android refuses that in the background, the run continues when the app is
  opened.
- **Runs.** A list of runs, newest first, with duration and point count. Long press:
  Delete (confirmed) or Merge with previous run.
- **Export.** Accuracy, minimum distance and interval filters from presets, a live count
  of the points kept, and Share GPX. A pause or reboot gap starts a new track segment.
- **Settings mode.** GPS source, speed units, debug NMEA log and sharing it, theme,
  orientation, navigation bar, app permissions, update check.
- **Debug NMEA log.** Optionally tees the raw NMEA stream of a run, with the chipset's
  model and capabilities for the internal GPS, to a shareable text file.
- **Self-update.** A user-triggered check against the home.codevoid.de dist store, the
  release channel for a release build and the nightly channel for a dev build, with
  progress on the tile and a hand-off to the system installer.
- **Themes and sizing.** Dark (default) and light themes; glove sizing for the 7" device
  with a scaled-down phone variant; landscape and portrait layouts.
