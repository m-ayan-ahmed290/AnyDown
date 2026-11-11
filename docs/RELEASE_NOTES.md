AnyDown 2.1.1 addresses TikTok's "Unable to extract webpage video data" failure, even when cookies are supplied.

- TikTok previews retain the full yt-dlp info JSON, including media URLs and HTTP headers, in a private five-minute cache. Downloads use that snapshot rather than immediately extracting the TikTok page again. Queued/older items refresh expired or missing snapshots before downloading.
- A TikTok page-parser failure triggers one official yt-dlp update per app session and one extraction retry, respecting the selected Stable/Nightly channel. Login, private-content and rate-limit errors do not trigger this recovery.
- A failed snapshot download with HTTP 403/410 or an expired URL refreshes its media links once, then stops if the retry fails. It does not loop or repeatedly hammer TikTok. Snapshots are cleared when TikTok cookies are replaced/removed.
- Short, actionable errors replace the long upstream issue-report text. Cookies are not incorrectly blamed for this parser failure.

Validation: the reported @thebignukey post (7643629243930348831) was successfully extracted and downloaded here with official stable yt-dlp 2026.10.26, without cookies. ffprobe confirmed H.264 video at 720×1280 plus AAC audio. The exact preview-info-json → load-info-json workflow was exercised on that post. This host test does not establish identical behavior on every phone/network. Unit tests cover recovery classification, cache contents/expiry and invalidation; Android build/lint checks run in CI.

Install over v2.1.0; the signing key is unchanged. On an older app, try Settings → Download engine → Stable → Update yt-dlp, then retry the failed item. If Stable still fails, try Nightly. Provider restrictions and challenges can still affect availability.
