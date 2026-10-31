# User guide

Recording is controlled by touch or by the remote. Everything else (stopping a ride,
the run list, export and settings) is done by touch. The remote can pause a ride but never
end it, and it never reaches a screen you cannot leave with gloves on.

## Before the first ride

1. Open motoTracklog. Android asks for location access: allow **precise** location.
   Allow notifications too; the recording notification is how you stop a ride without
   opening the app.
2. Android then asks for location **all the time**. This lets a ride continue after the
   device restarts. Without it, a ride interrupted by a reboot resumes only when you open
   the app.
3. Allow the app to ignore battery optimization, so Android does not stop a long ride.

Declined something? **Settings → Permissions** opens the app's Android settings.

The status word on the Record screen tells you whether a ride can start:

| Status | Meaning |
|---|---|
| Ready | Press Start. |
| GPS disabled | Location is switched off on the device. |
| Unavailable | Precise location is not allowed. |
| No GPS device | The device has no GPS. Choose a Bluetooth receiver. |

## Record a ride

1. Press **Start**, by touch or with Enter on the remote. The status changes to
   **Searching**, then to **Recording** once the receiver has a fix.
2. While recording, the screen shows satellites in use, accuracy, recorded points,
   update rate, speed and the receiver's time.
3. **Pause** releases the receiver. Nothing is recorded and no power is used until you
   press **Resume**. A paused ride stays paused across a restart.
4. **Stop** ends the ride. It is touch-only. The notification also offers Pause,
   Resume and Stop.

You can leave the app while recording; the ride continues in the background. If the
device restarts, the same ride continues until you stop it.

The status also names trouble during a ride: **Receiver off** when the Bluetooth
receiver is out of reach or the GPS engine stopped, **GPS disabled** when Location was
switched off. The ride resumes on its own once the cause is gone.

## Use the remote

| Key | Record screen | Runs, Export, Settings |
|---|---|---|
| Dpad | Move to the Start / Pause / Resume tile | — |
| Enter | Start, pause or resume | — |
| Escape | Leave settings, then close the app | Back |

A held Escape does the same as a short one. Runs, Export and Settings are touch-only; on
those screens the remote only goes back.

## Export a ride as GPX

1. On the idle Record screen, tap **Latest run**. Or tap **Runs** and tap any ride.
2. Set the filters. Each tile opens a list of values:
   - **Accuracy** drops fixes less accurate than the limit.
   - **Min. distance** keeps a fix only after you have moved that far.
   - **Interval** keeps one fix per interval. It has no effect while a distance is set.
3. The middle of the screen shows how many points the filters keep.
4. Tap **Share GPX** and choose where to send or save the file.

The filters apply only to the export. The recorded ride always keeps every fix, so you
can export it again with other filters.

## Merge or delete rides

In **Runs**, long press a ride:

- **Merge with previous run** joins it with the ride listed below it. The merged ride
  keeps the earlier start. Use it to rejoin a ride you stopped by mistake.
- **Delete** removes the ride after asking. This cannot be undone.

The ride being recorded has no menu.

## Record from a Bluetooth receiver

1. Pair the receiver in Android's Bluetooth settings.
2. In motoTracklog, open **Settings → GPS source**. On Android 12 and newer, allow
   Bluetooth access when asked.
3. Pick the receiver. **Internal GPS** switches back.

The source cannot be changed while a ride is recording. If the receiver drops out, the
app reconnects on its own, quickly when the receiver comes back into range.

## Diagnose a receiver

Switch **Settings → Debug NMEA log** on and record a ride. The raw data the receiver sent
is kept in a text file. **Share debug log** sends the newest one.

## Adjust the app

All of these are in **Settings** on the Record screen. Tap **Settings** again, or press
Escape, to return.

| Setting | Options |
|---|---|
| Units | km/h, mph |
| Theme | Dark, Light |
| Orientation | Sensor, Landscape, Portrait, Reverse landscape, Reverse portrait |
| Navigation bar | Hidden, Visible |

## Update the app

**Settings → Check for updates** looks for a newer build on GitHub and offers to
install it. The app connects to the internet only when you tap it.
