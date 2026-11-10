# Social Downloader (Android)

Native Android app (Kotlin + Jetpack Compose) that downloads videos from X/Twitter, Reddit, Instagram Reels,
Threads, YouTube, TikTok, Facebook and anything else [yt-dlp](https://github.com/yt-dlp/yt-dlp) supports.
Everything runs **on the device**: yt-dlp (bundled Python) + FFmpeg + QuickJS via
[youtubedl-android](https://github.com/yausername/youtubedl-android). No backend, no API keys, no accounts.

- Package: `com.social_downloader` · minSdk 28 (Android 9) · targetSdk 35
- Output: a single **.mp4 with video + audio merged** (H.264/AAC preferred), saved to `Downloads/SocialDownloader`
- Optional audio-only (M4A)

## Features
URL box with paste/clear · automatic platform detection · preview (thumbnail, title, uploader, duration) ·
quality picker with size estimates · progress with speed and ETA · queue (1–3 parallel, cancel, retry) · history
(open, share, delete) · Library tab of downloaded videos (play, share, delete files) · optional sign-in for login-gated sites · Android Share sheet ("Share → Social Downloader") · foreground-service downloads with
notifications · light/dark/dynamic theme · Wi-Fi-only option · in-app yt-dlp updater (stable/nightly) ·
clear, human-readable errors for unsupported / private / login-gated / rate-limited links.

## Build
Requires JDK 17 and the Android SDK (platform 35).
```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```
Or open the folder in Android Studio (Ladybug or newer) and press Run.

## GitHub + automatic APK builds
```bash
git init && git add . && git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<you>/social-downloader.git
git push -u origin main
```
Every push runs `.github/workflows/build.yml` (tests + `assembleDebug`). Download the APK from
**Actions → latest run → Artifacts → SocialDownloader-debug-apk**. Push a tag (`git tag v1.0.0 && git push --tags`)
to also publish it as a GitHub Release. Install it with "install unknown apps" enabled.
Debug APKs are signed with a throw-away debug key, so updating a build from another machine needs an uninstall first.

## Architecture
```
engine/    YtDlpEngine (init, info, update) · UrlTools (extract/validate/detect) · QualityBuilder · ErrorMapper
download/  DownloadManager (queue, yt-dlp run, progress) · DownloadService (foreground) · MediaSaver · Notifications
data/      DownloadItem/Store (JSON history) · SettingsStore
ui/        Compose screens: Home, Queue, History, Settings + MainViewModel
```
Flow: link → validated (http/https, public hosts only) → `yt-dlp --dump-json` → quality list → download into app cache
with `-f bv*+ba/b -S res:<N>,vcodec:h264,acodec:aac --merge-output-format mp4 --remux-video mp4` → FFmpeg merges →
file copied to Downloads via MediaStore (Android 10+) or direct write (Android 9, asks for storage permission).

## Permissions
`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE(_DATA_SYNC)`, `POST_NOTIFICATIONS` (Android 13+, optional),
`WRITE_EXTERNAL_STORAGE` (Android 9 only). No backups, no cleartext traffic, non-exported service/provider.

## Limitations (please read)
- **Not verified by an Android build here.** The pure logic (URL validation, platform detection, quality building,
  error mapping) was compiled and unit-tested; the Android/Compose/Gradle parts were written against the library's
  real API but not built or run on a device. If CI reports a compile error, fix it from the log (usually a one-liner).
- Site support is whatever the bundled yt-dlp supports. Sites change constantly; use **Settings → Update yt-dlp**
  when something breaks. Threads support in particular depends on the yt-dlp version.
- **Instagram, Facebook, X (sensitive) and some TikTok/Threads posts often require login.** Use
  **Settings → Accounts** to sign in with your *own* account (in-app WebView) or import a `cookies.txt`. Only session
  cookies are stored, privately, and sent only to that site. The app never bypasses private, geo-blocked or DRM
  content you couldn't view yourself. YouTube is excluded (Google blocks WebView sign-in). Heavy automated use can
  get an account rate-limited, so a secondary account is wise.
- YouTube may show bot checks or throttle on some networks; the app retries once with alternate YouTube clients.
- Single videos only (playlists/profiles are not downloaded). Live streams are not supported.
- 4K/VP9/AV1 results are merged into MP4 as-is and some players may not decode them; pick 1080p or lower for H.264.
- APK is large (~100 MB universal) because Python + FFmpeg are bundled; trim `abiFilters` in `app/build.gradle.kts`.
- `minifyEnabled` is off for release builds on purpose; no release signing is configured.

## Legal
Only download content you own or have permission to save; respect each platform's terms and copyright law.
License: GPL-3.0 (required by the bundled youtubedl-android/yt-dlp/FFmpeg components); see `LICENSE`.
