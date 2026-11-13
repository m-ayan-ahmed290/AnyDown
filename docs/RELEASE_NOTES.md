AnyDown 3.0.1 adds 22 features, plus broad persistent in-app appearance controls. Install over the existing release; the package ID and signing key are unchanged.

New features (existing search, site filters, audio formats and theme modes are not counted again):
1. Batch preparation and queueing of up to 25 media links, with progress and cancellation. Identical links and already pending URLs are skipped.
2. A batch failure list and retry of failed batch links.
3. Saved preferred video resolution for future previews/batches.
4. A saved audio-only preset: mode, format and bitrate.
5. Pause/resume of waiting queue items; active transfers continue.
6. Download-next priority for queued items without changing their original timestamps.
7. Retry all failed history items.
8. Clear canceled history entries without deleting media files.
9. Aggregate active network download-speed display, excluding stale rates on converting/saving items.
10. Persistent favorites and a favorites-only library view.
11. Audio-only/video-only library filters.
12. Oldest-first and smallest-first sorting, in addition to the existing newest/largest choices.
13. A last-seven-days library filter.
14. Recorded audio/video storage totals, excluding files detected as missing.
15. Search history by title or platform.
16. A failed-only history filter.
17. Download again from a history/library entry, with a fresh preview.
18. Open the original website link from an entry.
19. Copy source links from download/history cards.
20. Copy the displayed failure message.
21. In-app personalization: seven palette choices plus a custom RGB accent (with contrast-adjusted shades), text scaling that respects system font scaling, three typefaces, three corner styles, three content widths, compact download cards, inactive navigation labels, thumbnail visibility, home counters, initial directory state, startup screen, in-app name and home message. The active tab retains its label; hidden inactive labels retain accessibility names. Reset restores customization and presets without removing files, history, favorites or cookies.
22. Customization export/import through Android's file picker. JSON backups contain appearance and download presets only, excluding accounts, cookies, history and files. Unsupported or oversized backups are rejected.

Where to find them: Settings → Customize your app; Download → Batch download links; Queue controls; History controls; Library filters and entry actions.

Limits: customization controls AnyDown's own interface, not the launcher label, Android permissions or third-party login pages. Queue pause does not suspend an active transfer. Keep the app open while batch links are prepared. Imported files without a website URL cannot be opened/redownloaded from a source link. Higher-resolution/bitrate presets cannot improve source quality. Public/private site access still depends on yt-dlp and the provider.
