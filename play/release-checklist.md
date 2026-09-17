# Release checklist

Work through it in order for the first release, and repeat the build and
verification steps for every release after that.

## One-time setup

- [ ] **Create the upload key** and keep it outside the repository, with a backup. Losing it means asking Play support for a key reset.
  ```sh
  keytool -genkeypair -v -keystore ~/rubify-upload.jks -alias rubify \
    -keyalg RSA -keysize 4096 -validity 10000
  ```
- [ ] Add the signing properties to `~/.gradle/gradle.properties` (never to the repo):
  ```properties
  rubifyKeystoreFile=/home/<user>/rubify-upload.jks
  rubifyKeystorePassword=...
  rubifyKeyAlias=rubify
  rubifyKeyPassword=...
  ```
- [ ] Create the app in Play Console and use **Play App Signing** (Play holds the app signing key; this key is only the upload key).
- [ ] Host `play/privacy-policy.md` at a public URL, with the placeholders filled in.
- [ ] Decide how to show ML Kit's third-party notices. The AARs ship a `third_party_licenses.txt` that isn't packaged today. Options: the `oss-licenses` Gradle plugin, which adds a library and an activity, or a build step that copies the notices into the assets for `LicensesActivity`.
- [ ] Settle the store listing (`play/listing.md`) and fill in its placeholders.

## Every release

- [ ] Bump `versionCode` (+1, never reused) and `versionName` in `app/build.gradle.kts`.
- [ ] If the disclosure strings changed in meaning, bump `Consent.DISCLOSURE_VERSION` and update `play/`.
- [ ] Build and check:
  ```sh
  ./gradlew clean testDebugUnitTest lintRelease bundleRelease
  ```
  `verifyReleaseNoNetworkPermission` must pass. The signed bundle is `app/build/outputs/bundle/release/app-release.aab`.
- [ ] Test a release build on a real device. Before the upload key exists, sign the unsigned APK with the debug key:
  ```sh
  ./gradlew assembleRelease
  apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out rubify-release-test.apk app/build/outputs/apk/release/app-release-unsigned.apk
  adb install -r rubify-release-test.apk
  ```
  Check: disclosure → enable → button reading → Refresh / Hide / Close → tile → withdraw consent → rotation.
- [ ] Upload to the **internal testing** track first, then promote.

## Play Console forms (first release, and whenever the answers change)

- [ ] Accessibility API declaration and demo video: `play/accessibility-declaration.md`
- [ ] Data safety: `play/data-safety.md`
- [ ] Privacy policy URL
- [ ] App access: no login. Give reviewers the steps to enable the service: open Rubify → Agree → Open accessibility settings → Rubify → On, then use the accessibility button on any page with Chinese text.
- [ ] Content rating questionnaire (education, no user content, no ads)
- [ ] Target audience: 13+ is suggested (see the privacy policy's Children section)
- [ ] Ads: none
- [ ] Countries and pricing
