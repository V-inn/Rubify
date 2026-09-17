# GitHub releases

Pushing a tag `vX.Y` that matches `versionName` runs
`.github/workflows/release.yml`. The workflow tests, lints and builds signed
per-ABI APKs, then publishes them as a GitHub release. Every push to `main`
and every pull request also runs `.github/workflows/ci.yml` (debug build,
unit tests, lint).

## One-time setup

The APKs are signed with the same **upload key** as the Play bundle (see
`release-checklist.md` to create it). Play re-signs its copies with the Play
app signing key, so a GitHub install and a Play install of Rubify have
different signatures. Switching between them means uninstalling first.

Add four repository secrets (**Settings → Secrets and variables → Actions**), for example with the GitHub CLI:

```sh
base64 -w0 ~/rubify-upload.jks | gh secret set RUBIFY_KEYSTORE_BASE64
gh secret set RUBIFY_KEYSTORE_PASSWORD   # prompts for the value
gh secret set RUBIFY_KEY_ALIAS           # e.g. rubify
gh secret set RUBIFY_KEY_PASSWORD
```

- The workflow refuses to publish when the key is missing or the APKs come out unsigned.
- Secrets aren't available to pull requests from forks, and the release workflow runs only for tags pushed to this repository.
- Anyone with push access to tags can publish a release. Consider a tag protection rule (**Settings → Rules**).

## Cutting a release

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, and commit.
2. Tag and push:
   ```sh
   git tag v0.2
   git push origin v0.2
   ```
3. Watch the **Release** workflow. It fails if the tag doesn't match `versionName`.

Release assets:

| File | For |
| --- | --- |
| `rubify-<version>-arm64-v8a.apk` | Almost all current phones and tablets (about 16 MB) |
| `rubify-<version>-armeabi-v7a.apk` | Older 32-bit ARM devices |
| `rubify-<version>-x86_64.apk`, `-x86.apk` | Emulators and some Chromebooks |
| `rubify-<version>-universal.apk` | Any device, when unsure (about 46 MB) |
| `SHA256SUMS.txt` | Checksums |

To build the same APKs locally (signed if the `rubifyKeystore*` properties are set):

```sh
./gradlew assembleRelease -PrubifyAbiSplits=true
```

## Installing from GitHub (text for users)

1. Download `rubify-<version>-arm64-v8a.apk` from the latest release. If it won't install, use the `universal` APK.
2. Open it and allow your browser or file manager to install apps when asked.
3. Open Rubify, read the disclosure, and tap **Agree and continue**.
4. Tap **Open accessibility settings** and turn Rubify on.
   **If Rubify is greyed out** (Android 13 and newer block accessibility for downloaded apps): open **Settings → Apps → Rubify**, tap the **⋮** menu, choose **Allow restricted settings**, then go back and turn Rubify on. Rubify's main screen shows an **Open app info** shortcut for this.

**Updates:** [Obtainium](https://github.com/ImranR98/Obtainium) can install
Rubify from this repository's releases and keep it up to date. Add the
repository URL, and set the APK filter to `arm64-v8a` (or `universal`).
