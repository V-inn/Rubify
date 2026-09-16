# Rubify

Rubify is an Android app that shows pinyin above the Chinese characters on your screen, to help with reading while you study Mandarin.

Tap the system **accessibility button** and Rubify takes a silent screenshot, recognizes the hanzi on it, and draws the pinyin for each character right above it. You don't leave the app you're reading in, and you don't take screenshots by hand. It works in ordinary apps (browser, WeChat, PDF readers) and on book pages opened in Samsung Notes.

## How it works

```
accessibility button tap
  -> AccessibilityService.takeScreenshot()      silent, no dialog or notification
  -> ML Kit Text Recognition v2 (Chinese)       on-device; one bounding box per character
  -> word segmentation + pinyin lookup          picks the right tone for polyphonic characters (多音字)
  -> TYPE_ACCESSIBILITY_OVERLAY window          pass-through, so touches reach the app below
```

- A second tap hides the annotations. They also hide on a timeout.
- A Quick Settings tile is the fallback trigger for full-screen apps, which hide the navigation bar and the accessibility button with it.

## Status

Early development. Done so far:

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Service skeleton: registers the accessibility button and logs taps | Done, verified on device |
| 2 | Screen capture with `takeScreenshot()`, debug builds save PNGs | Done, verified on device |
| 3 | OCR with ML Kit (Chinese), per-character bounding boxes | Planned |
| 4 | Pinyin with correct tones, with word segmentation | Planned |
| 5 | Overlay aligned to characters, adjusted for scale and DPI | Planned |
| 6 | Toggle on second tap, Quick Settings tile | Planned |
| 7 | Robustness: rotation, scroll and zoom | Planned |
| 8 | Publishing prep: consent screen, store disclosure, Play declaration form | Planned |

Not in the MVP: a pipeline that pre-renders pinyin into PDFs, live OCR of handwritten S Pen ink, Cantonese, full translation, and a live camera mode.

## Requirements

- Android 11 (API 30) or newer, the minimum for `takeScreenshot()`
- To build: JDK 17+ and an Android SDK with platform 36

## Build and install

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected device
./gradlew testDebugUnitTest lintDebug
```

Create `local.properties` with `sdk.dir=/path/to/Android/Sdk` if `ANDROID_HOME` is not set.

## Try it on a device

1. Install the debug build and open **Rubify**.
2. Tap **Open accessibility settings**, enable **Rubify**, and allow it.
3. If the system asks, assign the accessibility button (or gesture) to Rubify. If another service also uses the button, each tap opens a menu: pick Rubify. The capture waits 400 ms so the menu is gone by then.
4. Watch the log, then tap the accessibility button:

   ```sh
   adb logcat -s Rubify
   ```

   Each tap logs `Accessibility button clicked`, then `Screenshot <width>x<height>` and the path of the saved PNG. Nothing shows on screen yet, because the overlay arrives in phase 5.
5. Pull a debug screenshot and compare it with the screen:

   ```sh
   adb shell run-as com.rubify ls files/debug-screenshots
   adb exec-out run-as com.rubify cat files/debug-screenshots/<name>.png > shot.png
   ```

   Debug builds keep the 5 newest screenshots. Release builds never write them to disk.

The system allows about one screenshot per second. Taps faster than that log `INTERVAL_TIME_SHORT` and are ignored.

## Privacy

- Rubify acts only when you tap. It subscribes to no accessibility events and cannot read window content.
- Everything runs on the device. The app does not request the `INTERNET` permission.
- Screenshots stay in memory and are discarded after processing (debug builds are the only exception, see above). Backup and device transfer are disabled.

## Project layout

```
app/src/main/java/com/rubify/
  MainActivity.kt                      service status and link to settings
  service/RubifyAccessibilityService.kt  accessibility button callback
  capture/ScreenCapturer.kt            takeScreenshot() into a software bitmap
  capture/DebugScreenshotStore.kt      debug-only PNG dumps
app/src/main/res/xml/accessibility_service_config.xml   service capabilities
```

Contributor and agent guardrails live in [AGENTS.md](AGENTS.md).
