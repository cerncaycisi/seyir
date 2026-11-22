package dev.iptv.player.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

/** In-memory (per process) parental unlock: the PIN is asked once, then locked categories stay open. */
object ParentalSession {
    var unlocked by mutableStateOf(false)
}

object ParentalFilter {
    private val adultPattern = Regex(
        """(?<![\p{L}\p{N}])(xxx|adults?|\+18|18\+|yeti[sş]kin|porn\p{L}*|erotik|erotic)(?![\p{L}\p{N}])""",
        setOf(RegexOption.IGNORE_CASE),
    )

    fun isAdultName(name: String): Boolean = adultPattern.containsMatchIn(name)

    // Panels put fake "channels" between groups, e.g. "∞∞∞∞ TURK ULUSAL ∞∞∞∞" or "##### SPOR #####".
    private val separatorPattern = Regex("""^\s*([∞#=*~_•●▬\-]\s*){3,}""")
    fun isSeparatorName(name: String): Boolean = separatorPattern.containsMatchIn(name)

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("iptv-pin:$pin".toByteArray())
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    /** [stored] may be empty (no PIN), hashed, or a legacy plain 4-digit value. */
    fun verifyPin(stored: String, entered: String): Boolean =
        stored.isEmpty() || stored == hashPin(entered) || stored == entered

    fun streamKey(playlistId: String, type: ContentType, streamId: String) = "$playlistId|$type|$streamId"
    fun categoryKey(playlistId: String, type: ContentType, categoryId: String) = "$playlistId|$type|$categoryId"
}

/** Snapshot of everything that decides whether a channel/category is shown. */
data class ContentRules(
    val hiddenChannels: Set<String> = emptySet(),
    val hiddenCategories: Set<String> = emptySet(),
    val lockedCategories: Set<String> = emptySet(),
    val hideAdult: Boolean = false,
    val unlocked: Boolean = false,
) {
    fun isCategoryLocked(playlist: Playlist, type: ContentType, categoryId: String): Boolean =
        !unlocked && ParentalFilter.categoryKey(playlist.id, type, categoryId) in lockedCategories

    fun isCategoryMarkedLocked(playlist: Playlist, type: ContentType, categoryId: String): Boolean =
        ParentalFilter.categoryKey(playlist.id, type, categoryId) in lockedCategories

    /** Hidden channels/categories and adult names are removed; locked categories are kept (and shown with a lock). */
    fun filterBase(playlist: Playlist, type: ContentType, content: Content): Content {
        val catName = content.categories.associate { it.id to it.name }
        val categories = content.categories.filter {
            ParentalFilter.categoryKey(playlist.id, type, it.id) !in hiddenCategories && !(hideAdult && ParentalFilter.isAdultName(it.name))
        }
        val visibleCats = categories.mapTo(HashSet()) { it.id }
        val knownCats = catName.keys
        val items = content.items.filter {
            ParentalFilter.streamKey(playlist.id, it.type, it.id) !in hiddenChannels &&
                (it.categoryId in visibleCats || it.categoryId !in knownCats) &&
                !(hideAdult && ParentalFilter.isAdultName(it.name)) &&
                !(type == ContentType.LIVE && ParentalFilter.isSeparatorName(it.name))
        }
        return Content(categories, items)
    }

    /** For flat lists (favorites, search) where only the stream is known. */
    fun streamAllowed(playlist: Playlist, s: Stream): Boolean =
        ParentalFilter.streamKey(playlist.id, s.type, s.id) !in hiddenChannels &&
            ParentalFilter.categoryKey(playlist.id, s.type, s.categoryId) !in hiddenCategories &&
            !isCategoryLocked(playlist, s.type, s.categoryId) &&
            !(hideAdult && ParentalFilter.isAdultName(s.name))

    /** Items of [content] that may be listed right now (drops items of still-locked categories). */
    fun unlockedItems(playlist: Playlist, type: ContentType, items: List<Stream>): List<Stream> =
        if (unlocked || lockedCategories.isEmpty()) items else items.filter { !isCategoryLocked(playlist, type, it.categoryId) }
}
