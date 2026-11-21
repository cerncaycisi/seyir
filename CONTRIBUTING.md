# Contributing

Thanks for helping! / Katkılarınız için teşekkürler.

- Open an issue before large changes.
- Build: `./gradlew testDebugUnitTest assembleDebug` (JDK 17). Keep it green.
- Style: see `.editorconfig` (Kotlin official style, 4 spaces, LF).
- User-visible text goes in `res/values*/strings.xml` (see `docs/I18N.md`).
- **Never** commit real stream URLs, playlists, provider credentials, API keys or keystores. Use `tools/mock-xtream.ps1` for testing.
- Contributions are licensed under Apache-2.0.
