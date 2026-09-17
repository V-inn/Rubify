# Accessibility API permission declaration

Answers for the Play Console **Policy > App content > Sensitive app
permissions > Accessibility API** declaration. Rubify isn't an accessibility
tool (`isAccessibilityTool` is not set), so it relies on the prominent
disclosure and consent route. Keep this file in step with
`res/xml/accessibility_service_config.xml`, the disclosure strings, and the
listing.

## Is the app's core purpose to help people with disabilities?

No. Rubify is a study aid for people learning Mandarin Chinese.

## Core functionality

Rubify shows pinyin (romanized pronunciation with tone marks) above Chinese
characters displayed by any app. The user taps the accessibility button, or
Rubify's Quick Settings tile. Rubify then captures the current screen,
recognizes the Chinese characters on the device (ML Kit, bundled model),
converts them to pinyin with word-aware readings, and draws the pinyin in an
overlay above each character. A small floating bubble refreshes the pinyin
after scrolling, hides it, or closes it.

## How and why the AccessibilityService API is used

The feature has to work over other apps' content without the user leaving
them. The service uses exactly these capabilities, each for that feature:

| Capability | Use |
| --- | --- |
| `flagRequestAccessibilityButton` | The main trigger: a tap starts or ends a reading session. |
| `canTakeScreenshot` (`takeScreenshot()`) | Captures the screen once per explicit user tap (button, tile, or the bubble's Refresh) to find the Chinese characters. There is no dialog and no persistent notification, so reading isn't interrupted. |
| `TYPE_ACCESSIBILITY_OVERLAY` windows | Draws the pinyin (the window ignores touches, so input still reaches the app underneath) and the small control bubble. |
| `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` (`GLOBAL_ACTION_BACK` on Android 11) | Only when a session starts from the Quick Settings tile: closes the panel so the app's content can be captured. |

Not used:
- No accessibility event types are subscribed. The service receives no events.
- `canRetrieveWindowContent` is false. The service can't read other apps' view hierarchies, text or controls.
- No gestures are performed and no text is entered.
- No settings are changed.

## Data accessed, collected and shared

- **Accessed:** the screen image at the moment of an explicit user tap.
- **Processing:** entirely on the device. Recognition, pinyin conversion and drawing happen in memory, and the image is discarded as soon as the pinyin is drawn.
- **Collected / stored / shared:** nothing. The app has no `INTERNET` permission, and the build fails if any dependency adds one.

## Prominent disclosure and consent

- On first launch, and whenever the service is triggered without consent, the app shows a full-screen disclosure (`ConsentActivity`). It explains what the service does, what it accesses, what it never does, and how data is handled, and offers **Agree and continue** or **Not now**.
- The app only offers the path to enable the service after the user agrees.
- If the service was enabled some other way, a tap on the button or tile opens the disclosure instead of reading the screen.
- Consent can be withdrawn from **Privacy and consent** on the main screen. The service then stops reading the screen.

## Demonstration video (unlisted YouTube link)

Record on a real device (screen recording), about 60–90 seconds, with no
personal content on screen:

1. Fresh install → open Rubify → the disclosure appears. Scroll through it, then tap **Agree and continue**.
2. Main screen → **Open accessibility settings** → enable Rubify, showing the system's permission dialog.
3. Open a page with Chinese text → tap the accessibility button → pinyin appears with the bubble. Scroll the page to show touches pass through, then tap **Refresh**.
4. Tap **Hide**, then **Show**, then **Close**.
5. Pull down Quick Settings → tap the Rubify tile → the panel closes and pinyin appears → tap the tile again to end.
6. Main screen → **Privacy and consent** → **Withdraw consent** → tap the accessibility button → the disclosure opens instead of a reading.
