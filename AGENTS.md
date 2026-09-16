# AGENTS.md — Rubify guardrails

Rules for anyone changing this repo, human or agent. `CLAUDE.md` imports this file, so there is one source of truth.

## What this is

Rubify is an Android app (Kotlin, plain Views, no Compose). A tap on the accessibility button takes a screenshot, runs on-device OCR for Chinese, converts the characters to pinyin, and draws the pinyin above each character in an accessibility overlay. See `readme.md` for the pipeline and phase status.

The implementation plan lives in `docs/`. That folder is **gitignored and local only**: it may be missing, and it must never be committed. The existing plan there is in Portuguese. Everything new is written in English.

## Language

Code, identifiers, comments, docs, commit messages and UI strings are all in **English**. User-facing text goes in `res/values/strings.xml`, never hardcoded.

## Commands

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug   # must pass before a change is done
./gradlew installDebug                                 # needs a device (adb devices)
adb logcat -s Rubify                                   # all app logs use this tag (LOG_TAG)
```

- Lint must report **no issues**. Fix the cause. Suppress only when the finding is wrong, and leave a comment saying why.
- The Gradle cache is warm, so `--offline` works for the current dependencies. A new dependency needs network access once.

## Toolchain lock

AGP **8.11.1**, Kotlin **2.2.20**, Gradle **8.13**, compile/target SDK **36**, JVM target **17**. They move together, because each one constrains the others (see the comment in `build.gradle.kts`). Never bump one alone.

- `minSdk = 30` is a hard floor, because `takeScreenshot()` needs API 30. Don't lower it.
- `applicationId = "com.rubify"` is permanent after the first Play upload.
- Keep dependencies minimal. Each new library needs a reason in a comment next to it. In use: ML Kit Text Recognition v2 Chinese, **bundled** model (`com.google.mlkit:text-recognition-chinese`). Still to come: a lightweight pinyin library with word segmentation.
- After adding a dependency, check the merged manifest for new permissions and components (`app/build/intermediates/merged_manifest/`). The debug APK is about 53 MB because ML Kit's native OCR library is packaged for 4 ABIs. A Play app bundle splits it per ABI.

## Accessibility scope: non-negotiable

Play approves the Accessibility API only for a narrow, clearly disclosed purpose, so the service must stay minimal.

- **Act only on explicit user triggers**: the accessibility button callback and, from phase 6, the Quick Settings tile. Nothing runs in the background.
- **No `accessibilityEventTypes`**, no event handling in `onAccessibilityEvent`, and no `canRetrieveWindowContent`. Never read `AccessibilityNodeInfo` trees.
- **No `android:isAccessibilityTool="true"`**. Rubify is a study aid, not an assistive tool. It goes through disclosure and consent instead.
- A new capability or flag in `res/xml/accessibility_service_config.xml` needs matching updates to the service description string, `readme.md` → Privacy, and the phase 8 disclosure. Ask the user before adding one.
- Don't switch to `MediaProjection` unless the user decides to.

## Privacy

- **On-device only.** Never add the `INTERNET` permission, analytics, crash reporting, or any network client without explicit approval. The user prefers no network but accepts it if something truly requires it. Ask first.
- ML Kit's telemetry transport merges `INTERNET` and `ACCESS_NETWORK_STATE`. `AndroidManifest.xml` removes both with `tools:node="remove"`, and OCR works without them (verified on device). The `verify<Variant>NoNetworkPermission` task in `app/build.gradle.kts` fails any build whose merged manifest requests a network permission. Don't weaken it.
- Screenshots live in memory. Whoever receives a `Bitmap` owns it and must `recycle()` it when done (see `RubifyAccessibilityService.onScreenshot`).
- Only `BuildConfig.DEBUG` builds may write screen content to disk, and only to app-private storage (`debug/DebugImageStore`). Release builds never persist it. The same applies to logging recognized text.
- Backup and device transfer stay disabled (`backup_rules.xml`, `data_extraction_rules.xml`).

## Architecture

```
com.rubify
  service/   AccessibilityService: button callback, pipeline wiring
  capture/   takeScreenshot() into a software bitmap
  ocr/       ML Kit wrapper + pure OcrPage model (per-char boxes)
  debug/     debug-only image dumps and OCR box rendering
  pinyin/    segmentation and tone-correct pinyin               (phase 4)
  overlay/   TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_TOUCHABLE     (phase 5)
  tile/      TileService fallback trigger                       (phase 6)
```

- Keep the service thin. Logic goes in its own package.
- Keep pure logic free of Android imports so plain JUnit can test it on the JVM: pinyin choice for polyphonic characters (多音字), mapping from screenshot pixels to overlay coordinates (density, rotation), and retention rules. Every such unit gets tests in `app/src/test`. Don't use Robolectric unless it becomes unavoidable.
- No heavy work on the main thread. Pipeline callbacks run on the `rubify-worker` HandlerThread, one at a time, and ML Kit runs on its own threads. A single `reading` flag in the service spans capture and OCR, so a tap is dropped while a reading is in progress.
- Bitmap ownership is explicit. Don't recycle a bitmap before every async consumer's callback has run. Queue those callbacks on the worker so they run after any synchronous work that still uses the bitmap.
- Log through `LOG_TAG`. Never log recognized text in release builds.

## Device findings

Test device: Galaxy Tab S9 FE (SM-X610), Android 16 (API 36), One UI, 1600x2560 at 340 dpi.

- `AccessibilityButtonController.isAccessibilityButtonAvailable` returned `false` at connect even though the button worked, and `onAvailabilityChanged` never fired. Don't gate behavior on it. Phase 6 must not hide the button path because of it.
- When another service shares the button (for example Google Reading mode), a tap opens a system chooser. A capture taken right after the click includes the chooser and its dim scrim, which is why `ScreenCapturer` waits `SETTLE_DELAY_MS` first.
- `takeScreenshot()` returns physical pixels at full display size (same as `adb exec-out screencap -p`).
- OCR takes about 550 ms on a full screen. Symbol boxes fit each hanzi closely. The status bar and app toolbars get recognized too, so the overlay should skip system bar areas.
- On a brush-style (kai) textbook page, about 95% of characters were right. Typical confusions: 拿/掌, 来/未, 她/地, 上/土/止, 太/大. Errors shift when the page moves by a few pixels.
- Symbol `confidence` is weak. Every wrong character was below 0.5, but about 20% of all characters were too, many of them correct. Don't use it as a hard filter. Word context (phase 4) is the better signal.
- The accessibility-button chooser makes fast repeated taps impossible, so the tap-dropped path hasn't been seen on a device yet.

## Working in phases

Work follows the 8 phases in `readme.md` → Status. A phase is done only when its done criterion holds **on a real device**. Don't claim that from a build alone. When you can't test on a device, say so and list the manual checks. Update the Status table when a phase changes state.

## Git

- Commit only when asked. Never commit `docs/`, `local.properties`, build output, keystores or credentials.
- Release signing, when it's added, reads from `~/.gradle/gradle.properties`, never from the repo.
- Keep commits small, one per phase or concern, with an imperative English subject.
