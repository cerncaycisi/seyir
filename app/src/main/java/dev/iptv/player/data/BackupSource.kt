package dev.iptv.player.data

/**
 * Seam between the phone-facing backup page and the real backup implementation (BackupManager,
 * owned by the data workstream). Calls happen on a background thread.
 *
 * TODO(merger): at app start set `BackupSource.current = object : BackupSource { ... }` delegating to
 * BackupManager.exportJson()/importJson(); wrap suspend functions with runBlocking if needed.
 */
interface BackupSource {
    fun exportJson(): String
    fun importJson(json: String)

    companion object {
        @Volatile var current: BackupSource = Unavailable
    }

    object Unavailable : BackupSource {
        override fun exportJson(): String = throw UnsupportedOperationException("Yedekleme bu sürümde etkin değil")
        override fun importJson(json: String): Unit = throw UnsupportedOperationException("Yedekleme bu sürümde etkin değil")
    }
}
