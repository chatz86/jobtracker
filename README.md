# JobTracker

A Wear OS job & patrol tracking app for clinical and site-safety rounds, with a
paired Android phone app that mirrors the watch in real time.

## What it does

**Watch app (`app/` module — source of truth)**

- Start a **Job**, **Foot Patrol** or **Vehicle Patrol** with a location and a
  list of security officers (SOs); a live timer runs while the job is active
- A short double-buzz every 2 minutes while a job is running, so a forgotten
  job is noticed in time to conclude it
- Ongoing notification + Ongoing Activity, so the active job is visible on the
  watch face and in recents while it runs
- Home-screen **tile** and **watch-face complications** showing live status
- History of the last 100 jobs with optional patient name, UMRN and notes
- Clearing history or deleting locations/SOs is protected by a PIN
  (the PIN itself is not stored in the app; only a salted SHA-256 digest)

**Phone app (`mobile/` module — read-only mirror)**

- Shows the active job, full history, and the location/SO list pushed from the watch
- Requests a full state sync on launch and whenever the watch reconnects

## Sync design

The two apps communicate over the Wearable **MessageClient** using the paths
defined in `Config.kt` (`/jobtracker/config`, `/jobtracker/active`,
`/jobtracker/history`, `/jobtracker/request`). Both modules **must keep the same
`applicationId`** — that is what pairs the message routing between devices.

The watch pushes its full state when the phone connects, when it is resumed, and
periodically (every 30 s) while a foreground service tracks an active job.

## Building

Open the project in Android Studio, or:

```bash
./gradlew :app:assembleDebug :mobile:assembleDebug   # build both APKs
./gradlew :app:lintDebug :mobile:lintDebug           # lint
```

- `app/` — Wear OS module (minSdk 30, targets the latest Wear OS)
- `mobile/` — phone companion module (minSdk 26)

Install to connected devices with:

```bash
adb -s <watch> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <phone> install -r mobile/build/outputs/apk/debug/mobile-debug.apk
```

## Releases

Release builds are unsigned until a signing config is added
(`assembleRelease` produces `-unsigned` APKs/AABs). Bump `versionCode` /
`versionName` in both module build files together for each release.

