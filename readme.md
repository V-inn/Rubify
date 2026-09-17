# Rubify

Rubify is an Android app that shows pinyin above the Chinese characters on your screen, to help with reading while you study Mandarin.

Tap the system **accessibility button** and Rubify takes a silent screenshot, recognizes the hanzi on it, and draws the pinyin for each character right above it. You don't leave the app you're reading in, and you don't take screenshots by hand. It works in ordinary apps (browser, WeChat, PDF readers) and on book pages opened in Samsung Notes.

## How it works

```
accessibility button tap
  -> AccessibilityService.takeScreenshot()      silent, no dialog or notification
  -> ML Kit Text Recognition v2 (Chinese)       on-device; one bounding box per character
  -> word segmentation + pinyin lookup          in-app dictionary; picks the word's reading for polyphonic characters (多音字)
  -> TYPE_ACCESSIBILITY_OVERLAY window          pass-through, so touches reach the app below
```

- Along with the pinyin comes a small floating bubble you can drag anywhere:
  - **↻ Refresh** reads the screen again, for example after you scroll.
  - **Eye** hides the pinyin or brings the same pinyin back, without reading again.
  - **✕ Close** ends the session. Tapping the accessibility button again does the same.
- Rotating the screen hides the pinyin, because it no longer lines up. Use Refresh to bring it back.
- Planned: a Quick Settings tile as a fallback trigger for full-screen apps, which hide the navigation bar and the accessibility button with it.

## Status

Early development. Done so far:

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Service skeleton: registers the accessibility button and logs taps | Done, verified on device |
| 2 | Screen capture with `takeScreenshot()`, debug builds save PNGs | Done, verified on device |
| 3 | OCR with ML Kit (Chinese), per-character bounding boxes | Done, verified on device |
| 4 | Pinyin with correct tones, with word segmentation | Done, verified on device |
| 5 | Overlay aligned to characters, adjusted for scale and DPI | Done, verified on device |
| 6 | Toggle on second tap, Quick Settings tile | Partly done: a second tap ends the session and the bubble can hide or close the pinyin. Still to do: the tile, and hiding on a timeout |
| 7 | Robustness: rotation, scroll and zoom | Partly done: rotation hides the pinyin, and after scrolling you tap Refresh (see below) |
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

   Pinyin appears above the characters, with the control bubble at the right edge. Each reading logs `Accessibility button clicked` (for button taps), `Screenshot <width>x<height>`, `OCR in <ms> ms: <lines> lines, <n> hanzi`, `Pinyin in <ms> ms`, and `Overlay shown with <n> labels`. Debug builds also log every recognized line with its pinyin, like `你(nǐ)好(hǎo)` (`-s Rubify:D`), and each character with its box and confidence (`-s Rubify:V`). 
5. Pull the debug images and compare them with the screen:

   ```sh
   adb shell run-as com.rubify ls files/debug-images
   adb exec-out run-as com.rubify cat files/debug-images/<id>-screenshot.jpg > shot.jpg
   adb exec-out run-as com.rubify cat files/debug-images/<id>-ocr.jpg > ocr.jpg
   ```

   `-ocr.jpg` is the screenshot with OCR boxes drawn on it (lines in blue, hanzi in red, other characters in gray) and each hanzi's pinyin above its box. Debug builds keep the images of the 5 newest captures. Release builds never write them to disk.

Tapping the accessibility button during a reading cancels it. The system allows only about one screenshot per second (`INTERVAL_TIME_SHORT` in the log). Rubify retries a failed capture up to twice.

On a Galaxy Tab S9 FE, OCR of a full 1600x2560 screen takes about 550 ms, and pinyin for the whole page about 10 ms. The dictionary loads once, in about 1.7 s, when the service starts. On a printed textbook page in a brush-style (kai) font, about 95% of characters were read correctly. The confidence score is too noisy to filter out the errors.

## Pinyin

Rubify has its own small pinyin engine, because TinyPinyin, the library the plan suggested, has no tones.

- Each run of hanzi is split into words by maximum probability, using jieba's word frequencies (a unigram model). Each word then gets its phrase reading, and characters outside a known phrase get their default reading. This is how 银行 becomes yín háng, 行人 becomes xíng rén, and 我的确不知道 keeps dí què while 你的确认信息 gets de.
- Readings use tone marks and citation tones. 一 and 不 are always shown as yī and bù, with no tone sandhi (the source data applies sandhi inconsistently). A particle 了 is le and a structural 的 is de.
- Known limits:
  - Uncommon words that jieba doesn't know fall back to their characters' default readings. A curated list covers common ones (还书 huán shū, 长得 zhǎng de).
  - A standalone 过 reads guò, even as an aspect particle.
  - OCR mistakes carry through to the pinyin.

The dictionary lives in `app/src/main/assets/pinyin/`. It's generated, so don't edit it by hand. To rebuild it (Python 3, downloads pinned sources):

```sh
python3 tools/pinyin-data/build_pinyin_assets.py
```

Sources, all MIT licensed (notices ship in `assets/pinyin/LICENSES.txt`): [mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data), [mozillazg/phrase-pinyin-data](https://github.com/mozillazg/phrase-pinyin-data), and the dictionary of [fxsjy/jieba](https://github.com/fxsjy/jieba).

## Privacy

- Rubify reads the screen only when you tap: the accessibility button, or Refresh (or Show, after a rotation) on its bubble. It subscribes to no accessibility events and cannot read window content.
- The pinyin and the bubble live in accessibility overlay windows. The pinyin window ignores touches, so everything you do goes to the app underneath. Only the bubble itself takes touches.
- Everything runs on the device, with the OCR model bundled in the app. The app does not request the `INTERNET` permission. ML Kit's library asks for it (to send usage stats), so the manifest removes it, and the build fails if any network permission shows up.
- Screenshots stay in memory and are discarded after processing (debug builds are the only exception, see above). Backup and device transfer are disabled.

## Project layout

```
app/src/main/java/com/rubify/
  MainActivity.kt                      service status and link to settings
  service/RubifyAccessibilityService.kt  button callback, windows, capture -> OCR -> pinyin
  service/ReadingSession.kt            when to read and what to show (pure Kotlin)
  capture/ScreenCapturer.kt            takeScreenshot() into a software bitmap
  ocr/HanziRecognizer.kt               ML Kit Chinese text recognition
  ocr/OcrPage.kt                       OCR result model (pure Kotlin), isHanzi()
  pinyin/PinyinDictionary.kt           compact dictionary (pure Kotlin)
  pinyin/WordSegmenter.kt              max-probability word segmentation
  pinyin/PinyinAnnotator.kt            per-character pinyin for a line
  pinyin/PinyinAssets.kt               loads the dictionary from assets
  overlay/PinyinLayout.kt              label placement and sizing (pure Kotlin)
  overlay/PinyinOverlay.kt             full-screen, touch-through pinyin window
  overlay/ControlBubble.kt             draggable refresh / hide / close bubble
  debug/DebugImageStore.kt             debug-only JPEG dumps
  debug/OcrDebugRenderer.kt            draws OCR boxes for inspection
app/src/main/assets/pinyin/          generated pinyin dictionary
app/src/main/res/xml/accessibility_service_config.xml   service capabilities
tools/pinyin-data/                   dictionary build script
```

Contributor and agent guardrails live in [AGENTS.md](AGENTS.md).
