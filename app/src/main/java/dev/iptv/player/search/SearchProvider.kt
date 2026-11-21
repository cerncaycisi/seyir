package dev.iptv.player.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import dev.iptv.player.App
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.DeepLinks
import dev.iptv.player.data.ParentalFilter
import dev.iptv.player.data.Setting
import dev.iptv.player.data.TextSearch
import dev.iptv.player.data.db.ContentDb
import kotlinx.coroutines.runBlocking

/**
 * Android TV global-search suggestions backed by the cached channel/movie/series lists (ContentDb).
 * Hidden channels/categories, locked categories and (when enabled) adult names are never exposed.
 * The picked suggestion arrives at MainActivity as an iptvplayer://live|movie|series/... VIEW intent.
 */
class SearchProvider : ContentProvider() {
    private class Index(val rows: List<SearchRow>, val fetchedAt: Long, val builtAt: Long)

    @Volatile private var index: Index? = null

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val raw = selectionArgs?.firstOrNull() ?: uri.lastPathSegment?.takeUnless { it == SearchManager.SUGGEST_URI_PATH_QUERY }
        val query = SearchQuery.parse(raw) ?: return cursor
        val context = context ?: return cursor
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        val rows = runCatching { runBlocking { SearchLogic.rank(rows(context), query, limit) } }.getOrDefault(emptyList())
        rows.forEachIndexed { i, r ->
            val type = ContentType.valueOf(r.type)
            val link = when (type) {
                ContentType.LIVE -> DeepLinks.live(r.playlistId, r.id)
                ContentType.MOVIE -> DeepLinks.movie(r.playlistId, r.id)
                ContentType.SERIES -> DeepLinks.series(r.playlistId, r.id)
            }
            cursor.addRow(
                arrayOf<Any?>(
                    i.toLong(),
                    r.name,
                    listOfNotNull(SearchLogic.typeLabel(r.type), r.year?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    r.logo,
                    if (type == ContentType.LIVE) "video/live" else "video/mp4",
                    if (type == ContentType.LIVE) "true" else "false",
                    Intent.ACTION_VIEW,
                    link.toString(),
                    "${r.playlistId}|${r.type}|${r.id}",
                ),
            )
        }
        return cursor
    }

    private suspend fun rows(context: android.content.Context): List<SearchRow> {
        val dao = ContentDb.get(context).dao()
        val fetched = dao.lastFetched()
        val cached = index
        val now = System.currentTimeMillis()
        if (cached != null && cached.fetchedAt == fetched && now - cached.builtAt < REBUILD_MS) return cached.rows

        val settings = (context.applicationContext as App).repository.settings
        val hiddenChannels = settings.get(Setting.HiddenChannels)
        val hiddenCategories = settings.get(Setting.HiddenCategories)
        val locked = settings.get(Setting.LockedCategories)
        val hideAdult = settings.get(Setting.HideAdult)
        val built = dao.searchProjection().asSequence()
            .filter { p ->
                val type = runCatching { ContentType.valueOf(p.type) }.getOrNull() ?: return@filter false
                // Series entries are series; live/movie rows must be playable ids. Empty names never match anyway.
                ParentalFilter.streamKey(p.playlistId, type, p.id) !in hiddenChannels &&
                    ParentalFilter.categoryKey(p.playlistId, type, p.categoryId) !in hiddenCategories &&
                    ParentalFilter.categoryKey(p.playlistId, type, p.categoryId) !in locked &&
                    !(hideAdult && ParentalFilter.isAdultName(p.name))
            }
            .map { SearchRow(it.playlistId, it.type, it.id, it.name, TextSearch.normalize(it.name), it.logo, it.year) }
            .toList()
        index = Index(built, fetched, now)
        return built
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "dev.iptv.player.search"
        private const val REBUILD_MS = 10 * 60_000L
        private val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_IS_LIVE,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_SHORTCUT_ID,
        )
    }
}
