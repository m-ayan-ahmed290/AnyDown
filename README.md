# AnyDown

Native Android media downloader built with Kotlin, Jetpack Compose, yt-dlp and FFmpeg. Downloads run on your device. Android 9+; package `com.social_downloader` stays unchanged for upgrades.

## What's new

- Material You colors by default, light/dark/system themes, and an adaptive A/down-arrow logo with a monochrome Android 13 themed-icon layer.
- Bottom tabs highlight the **whole icon and label** together, with an accessible selected state and queue badge.
- Searchable directory covering major social/video/audio platforms: YouTube, Instagram, X, Reddit, TikTok, Facebook, Threads, Vimeo, Dailymotion, Twitch, SoundCloud, Pinterest, Snapchat, Bilibili, VK, Rumble, Tumblr, Bandcamp, TED, Bluesky, Streamable and Internet Archive. Other yt-dlp extractors are attempted for pasted links.
- Adult-site directory entries are **hidden by default**. Enable **Settings → Website directory → Show adult sites** to show them. This controls directory visibility, not a parental-control filter for manually pasted links.
- Quality selection, audio-only downloads, previews, share-sheet input, speed/ETA, cancellation/retry, foreground notifications, Wi-Fi-only mode, site sign-in, engine updates, and local playback/sharing.
- Search your library by title/filename, filter by website, and sort newest/largest. Bulk delete applies only to shown results.
- Up to **five parallel files** and **2/4/8/16 fragment connections** per file (8 default). Exponential retry delays handle transient failures. Parallel fragments help segmented streams; sites/network/device limits still apply. Single HTTP files are not artificially split.

## Storage and upgrade migration

New files default to `Downloads/AnyDowns/<Website>/`, for example `AnyDowns/Instagram/`. Turn off **Organize by website** to save directly in Downloads.

On launch the app idempotently migrates accessible `Downloads/SocialDownloader` files. On Android 10+ it updates app-owned MediaStore entries, preserving content URIs and library references. Android 9 moves files and updates stored paths after storage permission is granted. Known history determines the website; unknown files preserve existing subfolders or go into `Other`. Existing destination files are never overwritten. Partial moves retry on the next launch. No broad all-files permission is requested.

Android does not permit silent access to files owned by a different app/installation. Such files must be moved in a file manager. Keep the application ID and signing key to upgrade without losing storage ownership/history. The directory switch does not relocate files already saved under AnyDowns.

## Build and checks

JDK 17 and Android SDK platform 35 are required.

```sh
bash ./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions and releases

`.github/workflows/android.yml` tests, lints and builds on main/work pushes, pull requests and manual dispatch. Download **AnyDown-apk** from the run's artifacts. Push a `v*` tag to publish a signed GitHub release with an installable APK and SHA-256 checksum. The workflow only publishes after checks pass.

Release tags require these Actions secrets in the **AnyDown** environment (or repository secrets): `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The keystore is decoded only into runner temporary storage and removed after signing. Production tags fail clearly if signing is missing. Regular builds still provide a debug APK when secrets are absent. Do not commit private signing material; keep a permanent backup so updates retain the same signing identity. Locally set `ANDROID_KEYSTORE_PATH` plus the three non-Base64 values before `assembleRelease`.

## Limits and privacy

Site coverage follows yt-dlp, not the presence of a directory entry. Some links require your own signed-in session, regional access or an engine update. No DRM bypass. Playlists/profiles and live streams are not supported. Google blocks embedded sign-in, so YouTube login is not provided. The app stores cookies privately and does not use a download backend or analytics. Actual download speeds depend on the source, network and device; no speed guarantee.

Only download media you have permission to save. GPL-3.0; see LICENSE.

## Account and recovery improvements in 2.1

Account actions expand on demand. Embedded login no longer clears sessions during loading, shows loading/errors/reload and offers Open browser. Google OAuth is blocked with guidance because providers can reject embedded browsers. External browser sessions are not automatically accessible; import a Netscape cookies.txt export. Imports retain only current cookies for the selected site. Cookies saved does not mean a server-verified account.

With Show adult sites enabled, the final Settings section provides adult account sign-in/cookie import. Six more adult platforms are recognized; actual availability is controlled by yt-dlp and the site.

If automatic migration missed old files, use Settings → Storage upgrade → Import old downloads. Choose multiple media files using Android's picker and optionally choose their website folder. Copies appear in AnyDowns and Library; originals remain untouched. The UI reports imported/skipped/failed counts and skips previously imported URIs while the saved copy exists. After uninstall, platform metadata may be unavailable; choose a folder explicitly or use Other. Copying requires space for staging and destination files. This is an explicit recovery path rather than a claim that Android grants silent access to another installation's files.

Signed work-branch builds now publish a release when versionName has no existing release. Existing releases are never overwritten. Tag-triggered releases remain available.

## TikTok recovery in 2.1.1

TikTok preview extraction now writes complete info JSON into a short-lived private snapshot. A download loads this info instead of repeating the page request. Snapshots expire after five minutes, and changing TikTok cookies clears them. Missing/expired snapshots are re-extracted. A stale media URL (403/410) gets one fresh extraction and retry. Recognized TikTok parser failures attempt one official engine update per app session using the selected update channel. Access restrictions and rate limits do not trigger automatic updates. The UI gives a short update/retry instruction instead of the upstream issue-report dump.
