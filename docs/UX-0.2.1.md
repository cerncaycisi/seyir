# 0.2.1 — review fixes and TV UX

## Review fixes

- History reads and removal now distinguish unprefixed default-profile keys from named-profile keys. Clearing movies, clearing all history, or removing a series no longer reaches another viewer's history.
- Android TV Watch Next deduplication includes content type: a movie and a series with the same provider ID are separate titles.
- Clicking a live channel updates the preview selection and programme details as well as its player. Pointer and D-pad selection now agree.
- Playback queue snapshots retain episode season/number across process recreation.
- Four new unit regressions exercise profile isolation and colliding provider IDs; the existing queue round-trip regression now includes season/episode metadata.

## Home

- One live-channel row combines recent and favourite channels, then fills remaining places from the catalog. Duplicate IDs and decorative infinity-prefixed category dividers are omitted.
- Removed the redundant recently-watched VOD row. Watch-later does not repeat titles already in continue-watching.
- Shorter hero leaves more room for content. The active playlist and a labelled refresh button stay visible above it.
- Refresh is explicit, disables repeated submissions, keeps the old catalog visible, and reports success or a recoverable error without displaying provider credentials.

## Player

- Labelled bottom controls: play/pause, VOD seek ±10 seconds, channel list, audio, subtitles, aspect ratio, live recording, and more options.
- OK opens the controls; arrows navigate them. Back first closes active controls, then leaves playback. Outside the controls, channel/seek shortcuts remain available.
- Audio, subtitle and aspect shortcuts open their selection page directly and return to the controls after selection.
- More options preserves playback speed, sleep timer, quality, online subtitles, PiP and multi-view; stream information adds resolution, frame rate and audio/video formats without stream URLs.
- Added a buffering indicator, remaining VOD time and a retry button. Play/pause labels follow playback intent, including pausing during buffering.

## Validation

- 118 unit tests pass; debug and R8 release builds complete. Lint has no errors (existing warnings remain).
- Real-provider emulator checks: live playback, opening controls, remote pause/resume, audio selection, VOD seek, Turkish subtitle selection, and successful playlist refresh.
- Home/player layouts inspected from screenshots at 1920×1080.
- A prototype was installed on the connected TCL TV before the user clarified that every physical-TV operation requires permission. Physical-TV work stopped immediately; subsequent checks and the final APK update were restricted to the emulator. Final TV installation and verification require user permission.
- No long-duration recording or playback soak test is claimed for this UX change.

Design references: [VLC Android player controls](https://docs.videolan.me/vlc-android-user/video/video_player.html) and [IBO Player](https://apps.iboplayer.com/). The implementation uses this app's existing playback features and visual style.
