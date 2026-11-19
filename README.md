# HTFU

HTFU is an offline workout tracker for Android. Pick a program (Stronglifts 5x5, 100 Pushups, Couch to 5K, 5/3/1, military and first-responder fitness tests, and many more) or build your own, log every set, and watch your numbers go up.

- **No internet permission.** The app cannot go online. There are no ads, no analytics, no trackers and no account.
- **Your data stays on your phone.** Workouts, settings and progress photos live in the app's own storage. Android cloud backup and device-to-device transfer are switched off for it; the in-app backup file is the only way data leaves the phone, and only when you save it.
- **No third-party libraries.** Only the Android framework is used.

## Features

- More than 40 built-in programs: bodyweight (100 Pushups, 20 Pullups, 100 Squats, Recommended Routine, Murph), barbell (Stronglifts 5x5, Starting Strength, 5/3/1, nSuns, GZCLP, Greyskull LP, Texas Method, Madcow 5x5, Push Pull Legs, Upper Lower, YOLO), kettlebell (Simple & Sinister), running (Couch to 5K, Bridge to 10K, Half Marathon Novice 1, Beep Test), fitness tests (Army AFT, Marine PFT, Navy PRT, Air Force PFRA, CF EXPRES, Ontario PREP and PIN, firefighter tests) and mobility routines
- Custom plans with any mix of reps, weight, time and distance
- Automatic progression and deloads, a rest timer that keeps running in a notification, and optional voice cues
- Hands-free rep counter using the proximity or motion sensor
- Plate calculator and % of 1RM calculator, exercise how-to guides
- Charts, personal bests, streaks, a calendar and milestones
- Body weight, measurements and optional progress photos
- Workout-day reminders (re-armed after a reboot) and a home-screen widget
- Backup and restore to a single file; Excel and CSV export; share plans and sessions with friends as files
- Imperial or metric units, six themes and a high-contrast colour scheme

## Permissions

| Permission | Why |
|---|---|
| `VIBRATE` | Rest timer and rep feedback |
| `POST_NOTIFICATIONS` | Workout reminders and the rest-timer notification |
| `RECEIVE_BOOT_COMPLETED` | Re-arm reminders after the phone restarts |
| `SCHEDULE_EXACT_ALARM` | Reminders and the rest timer go off on time |

That is the complete list. There is no `INTERNET` permission.

## How it is built

The app is a single offline web page (`app/src/main/assets/www/index.html`) shown in an Android `WebView`, plus a small Java layer (`app/src/main/java/app/htfu/`) for the things a web page cannot do: notifications, exact alarms, the boot receiver, the home-screen widget, vibration, the motion/proximity rep counter, text-to-speech cues, and saving/opening files through Android's file picker. The page talks to Java through `@JavascriptInterface` bridges (`AndroidExport`, `AndroidReminders`, `AndroidRest`, `AndroidVoice`, `AndroidLinks`). Links to outside sources open in your browser; the app itself never loads anything from the network.

The same page also runs as a web app at <https://htfu.app/>.

## Building

Requirements: JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

```sh
git clone https://github.com/PurpleCrow846/htfu.git
cd htfu
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleRelease
```

The unsigned APK is written to `app/build/outputs/apk/release/HTFU-1.0.apk`. Sign it with your own key:

```sh
apksigner sign --ks my.jks --out HTFU-1.0-signed.apk app/build/outputs/apk/release/HTFU-1.0.apk
```

`./gradlew assembleDebug` builds a debug-signed `HTFU-1.0-debug.apk` you can install directly.

## License

Copyright (C) 2026 PurpleCrow

HTFU is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

The page embeds subsets of these fonts, each under the SIL Open Font License 1.1 (texts in [`licenses/fonts/`](licenses/fonts/)): Oswald, Black Ops One, Bebas Neue, Archivo Black, Space Grotesk, Chakra Petch and Roboto Condensed.
