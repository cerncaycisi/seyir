package dev.iptv.player.data.db

import dev.iptv.player.data.Category
import dev.iptv.player.data.Codec
import dev.iptv.player.data.ContentType
import dev.iptv.player.data.PlaylistSource
import dev.iptv.player.data.Stream
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Converts between domain objects and rows, keeping credentials out of the database file:
 * - Xtream stream URLs embed username/password, which are replaced by a placeholder (rebuilt from the source on read);
 * - M3U URLs, catch-up templates and headers are encrypted with [codec].
 */
class ContentMapper(private val source: PlaylistSource, private val codec: Codec) {
    private val placeholder = "/{cred}/"
    private val xtreamCredentials: String? = (source as? PlaylistSource.Xtream)?.let {
        if (it.username.isBlank() || it.password.isBlank()) null else "/${it.username.trim()}/${it.password.trim()}/"
    }
    private val headersSerializer = MapSerializer(String.serializer(), String.serializer())

    fun toCategory(playlistId: String, type: ContentType, category: Category, ord: Int) =
        CategoryEntity(playlistId, type.name, category.id, category.name, ord)

    fun toCategory(entity: CategoryEntity) = Category(entity.id, entity.name)

    fun toEntity(playlistId: String, stream: Stream, ord: Int) = StreamEntity(
        playlistId = playlistId,
        type = stream.type.name,
        id = stream.id,
        name = stream.name,
        logo = stream.logo,
        url = sealUrl(stream.url),
        categoryId = stream.categoryId,
        epgId = stream.epgId,
        rating = stream.rating,
        year = stream.year,
        plot = stream.plot,
        backdrop = stream.backdrop,
        genre = stream.genre,
        headers = stream.headers.takeIf { it.isNotEmpty() }?.let { codec.encrypt(Json.encodeToString(headersSerializer, it)) },
        catchup = stream.catchup,
        catchupDays = stream.catchupDays,
        catchupSource = stream.catchupSource?.let(codec::encrypt),
        ord = ord,
    )

    /** Throws if an encrypted field cannot be decrypted (key lost); callers treat that as a cache miss. */
    fun toStream(entity: StreamEntity) = Stream(
        id = entity.id,
        name = entity.name,
        logo = entity.logo,
        url = openUrl(entity.url),
        categoryId = entity.categoryId,
        type = ContentType.valueOf(entity.type),
        epgId = entity.epgId,
        rating = entity.rating,
        year = entity.year,
        plot = entity.plot,
        backdrop = entity.backdrop,
        genre = entity.genre,
        headers = entity.headers?.let { Json.decodeFromString(headersSerializer, codec.decrypt(it)) }.orEmpty(),
        catchup = entity.catchup,
        catchupDays = entity.catchupDays,
        catchupSource = entity.catchupSource?.let(codec::decrypt),
    )

    private fun sealUrl(url: String): String = when {
        xtreamCredentials != null -> url.replace(xtreamCredentials, placeholder)
        source is PlaylistSource.M3u -> codec.encrypt(url)
        else -> url
    }

    private fun openUrl(stored: String): String = when {
        xtreamCredentials != null -> stored.replace(placeholder, xtreamCredentials)
        source is PlaylistSource.M3u -> codec.decrypt(stored)
        else -> stored
    }
}
