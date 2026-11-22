package dev.iptv.player.data

import dev.iptv.player.data.tmdb.TmdbInfo

/** A TMDB sort must never silently mix provider scores with TMDB scores. */
internal fun withCatalogMetadata(stream: Stream, info: TmdbInfo?, tmdbSort: Boolean): Stream = stream.copy(
    logo = stream.logo ?: info?.poster,
    backdrop = stream.backdrop ?: info?.backdrop,
    year = stream.year ?: info?.year,
    rating = if (tmdbSort) info?.rating else info?.rating ?: stream.rating,
    genre = info?.genre ?: stream.genre,
    plot = info?.plot ?: stream.plot,
)
