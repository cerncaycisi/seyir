# 0.2.2 — TV walkthrough changes

The user walked through the physical TV and requested these changes. Development and verification after that walkthrough use the emulator only; physical-TV installation, launch, input and screenshots require fresh permission.

## Navigation

- Removed the Guide screen and its menu entry.
- Main content sections are Home, Live TV, Movies and Series. Settings and Playlists remain below them.
- Recordings opens inside Live TV. Back returns to Live TV.
- Search opens from each section and searches only that content type. Movie and series favorites remain separate pinned categories inside their own browsers.
- The Settings menu scrolls and has explicit up/down focus neighbors. Profiles no longer steals focus on entry; it restores list focus only after leaving its editor.

## Discovery and details

- Movies and series both show a genre selector. TMDB adds missing movie genres, localized descriptions and ratings.
- “TMDB rating” uses only matched TMDB scores; provider scores do not silently enter that ordering. Unmatched titles have no score and sort last.
- Matching checks localized/original titles after normalization. A different sequel returned by search must not supply the rating or cast.
- Catalog metadata is cached on disk. Ordinary browsing loads a small opening batch; selecting a genre/rating loads the selected category progressively, four requests at a time. Progress remains visible, browsing stays responsive, and leaving cancels further work. A large uncached catalog is not instantly complete.
- Turkish descriptions take priority. If TMDB has only an English synopsis, the fallback is labelled rather than hidden or presented as Turkish. No automatic translation service is claimed.
- “Read more” opens the whole description in a bounded reader with D-pad scrolling and single-Back dismissal. Season labels are centered.
- Detail pages scroll only when the focused action would leave the viewport; the default TV pivot no longer clips the title on entry.

## Playback

- Removed aspect ratio and duplicate seek buttons from the main toolbar. Aspect ratio remains in More.
- The focusable timeline previews a position with left/right (10-second steps), applies with OK, cancels a draft with Back, and supports pointer seeking.
- Back closes subtitle/settings panels in one press, including subpages entered through More.
- Pausing shows cast and a synopsis preview. About this title opens full synopsis, genre, cast and director while staying in the player. Credits are fetched only for a selected detail/pause view.

## Verification

- Unit regressions cover TMDB/provider score separation, metadata application, title matching and seek bounds.
- 124 unit tests pass. Debug and R8 release builds pass; lint reports zero errors (78 warnings, including existing dependency/resource warnings).
- Emulator checks use the existing real playlist without clearing application data: scoped movie/series/channel search, genre choices, Recordings under Live TV, Settings navigation past Profiles to About, full synopsis reader, real VOD playback and a 20-second timeline seek, and one-Back subtitle dismissal.
- Final artifact is prepared locally. This version has not been installed on the physical TV.

References: [IBO developer feature listing](https://apps.apple.com/us/app/ibo-player/id1547652240), [VLC Android player controls](https://docs.videolan.me/vlc-android-user/video/video_player.html), [TMDB movie search](https://developer.themoviedb.org/reference/search-movie), [TMDB details](https://developer.themoviedb.org/reference/movie-details). The exact Spectre application used by the user could not be independently identified; no hands-on comparison is claimed.
