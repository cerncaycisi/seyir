package dev.iptv.player.data

/**
 * Seam between the phone-facing backup page and the real backup implementation (BackupManager,
 * owned by the data workstream). Calls happen on a background thread.
 *
 * Installed by [Repository] on creation (delegates to BackupManager).
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
