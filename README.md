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

`.github/workflows/android.yml` tests, lints and builds on main/work pushes, pull requests and manual dispatch. Download **AnyDown-apk** from the run's artifacts. Push a `v*` tag to publish a GitHub preview release with an installable APK and SHA-256 checksum. The workflow only publishes after checks pass.

The current workflow deliberately distributes **debug-signed previews**. Different ephemeral CI keys cannot update each other; uninstalling removes private history/cookies and may lose access to previously owned Downloads. Configure a stable private release keystore in GitHub Actions secrets before production distribution. Never commit a private signing key. No production key is included in this repository.

## Limits and privacy

Site coverage follows yt-dlp, not the presence of a directory entry. Some links require your own signed-in session, regional access or an engine update. No DRM bypass. Playlists/profiles and live streams are not supported. Google blocks embedded sign-in, so YouTube login is not provided. The app stores cookies privately and does not use a download backend or analytics. Actual download speeds depend on the source, network and device; no speed guarantee.

Only download media you have permission to save. GPL-3.0; see LICENSE.
