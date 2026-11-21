# Seyir 0.2.3 — background catalog metadata and subtitle styles

## Behavior

- Home's playlist refresh runs as a unique WorkManager job. Leaving Home does not cancel it; existing content stays usable. Home shows a small completion/failure message.
- Movie/series screens observe a persistent Room metadata index instead of starting a title scan on every entry or sort/filter change. The old metadata progress counter is removed.
- Index reconciliation runs off the foreground load path after a disk load or playlist refresh. Unchanged title/year identities retain their results, including genuine no-matches; new/changed titles become pending and removed titles leave the index. Provider URL, category, ordering and rating changes do not invalidate a title lookup.
- TMDB workers resolve pending entries in pairs, save progress, and chain bounded runs. Network failures remain pending and retry with backoff. In-flight results cannot overwrite a changed title. Pending work resumes after process restart or re-enabling TMDB.
- Existing TMDB cache files migrate lazily from cache storage to persistent app files. The separate metadata database does not change the content database or playlist data. First-time enrichment can still take time for a large catalog, but runs quietly without blocking browsing. The background catalog fetches basic metadata; cast/credits remain cached on-demand detail requests.
- Five persistent text subtitle presets: Clean white (default), Soft shadow, Cinema yellow, Dark background, Source style. Available under Settings → Player and the player's Subtitle menu, with a live sample rendered by the same Media3 view. Selection applies immediately; Back closes the player panel once. Size remains independently configurable. Bitmap subtitles retain their source appearance.

## Validation

- 127 JVM tests passed, including an unchanged/reordered 5,608-title catalog requiring zero new lookups and incremental identity changes.
- Two Android Room tests passed on `emulator-5554`: durable hits/no-matches, delta reconciliation, stale-result rejection, database reopen, playlist/type isolation and scoped removal.
- Debug APK, Android test APK, lint (0 errors, 79 warnings), and minified release APK built successfully.
- Real emulator playlist: Home refresh completed after navigating away to Series and returning; Series remained browsable without a metadata progress counter.
- Real persisted index after refresh: 19,626 movies / 5,648 series; 1,010 movie and 1,244 series resolutions persisted at the sample point. SQLite quick check passed. Full first-time enrichment was not awaited.
- After process restart, movie resolutions advanced to 1,050; comparison against the first snapshot found zero lost or replaced previously resolved entries.
- Real movie playback: D-pad style selection, yellow preview, restart persistence, player style menu, clean-white preview, subtitle track selection and single-Back dismissal verified. No AndroidRuntime crash logged. Style appearance was inspected through the in-player sample; an actual timed subtitle cue was not captured in the sampled movie segment.
- QA images and database snapshots live under ignored `app/build/reports/qa/`.

## Delivery

Version code 23 / version name 0.2.3. Updated only the emulator using `install -r`; app data/playlists preserved. No physical-TV operations were performed for this release. Release APK uses the existing debug signing fallback because a release keystore is not configured.
