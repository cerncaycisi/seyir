package dev.iptv.player.util

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only crash reporting: no network, no third-party SDK. The last uncaught exception is written
 * to filesDir/crash/last.txt and can be shown or shared manually by the user.
 *
 * Later, Sentry (or similar) could be added by initialising its SDK in App.onCreate behind an
 * explicit opt-in setting; keep this handler chained so the local log keeps working.
 */
object CrashReporter {
    private const val PREFS = "crash_reporter"
    private const val KEY_ENABLED = "enabled"
    private const val MAX_CHARS = 20_000

    @Volatile private var appContext: Context? = null

    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (isEnabled()) write(app, thread, error)
            } catch (_: Throwable) {
                // never let the reporter mask the original crash
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Local logging is on by default; nothing ever leaves the device unless the user shares it. */
    fun isEnabled(): Boolean =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getBoolean(KEY_ENABLED, true) ?: false

    fun setEnabled(enabled: Boolean) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    fun lastReport(): String? {
        val f = file(appContext ?: return null)
        return if (f.exists()) runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() } else null
    }

    fun clear() {
        appContext?.let { file(it).delete() }
    }

    /** Chooser intent that shares the last report as plain text, or null when there is none. */
    fun shareIntent(subject: String = "Crash report"): Intent? {
        val text = lastReport() ?: return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun file(context: Context) = File(File(context.filesDir, "crash").apply { mkdirs() }, "last.txt")

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val report = buildString {
            appendLine("Time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
            appendLine("App: ${context.packageName} $version")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        file(context).writeText(report.take(MAX_CHARS))
    }
}
