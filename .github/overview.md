# Overview

## Problem

A rider wants the raw track of a ride: every fix the receiver delivers, kept on the device,
exportable as GPX afterwards. The recorder runs on the motorcycle navigation device, an
Android tablet on the handlebar that is operated with gloves or with a handlebar remote
that sends only dpad, Enter and Escape. The device is mostly offline and its battery and
CPU belong to the navigation app.

General-purpose GPS loggers assume a bare finger and a phone in a pocket: small controls,
swipes, settings a remote can enter but not leave, and recording that smooths or thins
fixes on the way in.

## Solution

motoTracklog is a supporting app of motoLauncher and shares its look, input model and
distribution.

- **Raw recording in the background.** A foreground service records every fix from the
  internal GNSS chipset, or from a paired Bluetooth NMEA receiver, into one file per run.
  Nothing is filtered while recording.
- **A remote that cannot end a ride.** The remote reaches Start and Pause/Resume only.
  Stop, the run list, export and every setting are touch-only.
- **Pause costs nothing.** Pausing releases the receiver, so a paused run draws no power.
- **Survives the device.** A run resumes after a process kill or a reboot and continues
  until the rider stops it.
- **Export after the ride.** Accuracy, distance and interval filters are applied only at
  export, picked from presets, and the result is shared as GPX. Runs can be merged with
  the previous run or deleted.
- **Offline by default.** The only network use is the user-triggered update check.
