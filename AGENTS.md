# Device access

- Ask the user for explicit permission before operating a physical TV. This includes ADB installation, launching the app, remote input, and taking screenshots. An existing ADB connection is not permission to operate the TV.
- This is the user's explicit preference from this chat. Do development and routine device checks on the emulator until a specific TV operation is approved.
- Always select an ADB device explicitly with `-s`; both an emulator and a physical TV can be connected.
- Preserve playlists and app data. Use APK updates (`install -r`), never uninstall or clear app data for routine testing. Keep `android.injected.androidTest.leaveApksInstalledAfterRun=true` enabled.
