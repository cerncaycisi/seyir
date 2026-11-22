package dev.iptv.player.data.tmdb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbSeasonEpisodesTest {

    private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `extracts Turkish episode names w300 still URLs and runtime`() {
        val tr = listOf(
            obj("""{"episode_number":1,"name":"Karanlıkta Kaybolduğunuzda","still_path":"/still1.jpg","runtime":81}"""),
        )
        val parsed = parseTmdbSeasonEpisodes(tr)
        val ep1 = parsed[1]!!
        assertEquals("Karanlıkta Kaybolduğunuzda", ep1.name)
        assertEquals("${Tmdb.IMAGE_BASE}/w300/still1.jpg", ep1.still)
        assertEquals(81, ep1.runtimeMins)
    }

    @Test
    fun `generic Turkish placeholder names fall back to English while keeping still and runtime`() {
        val tr = listOf(
            obj("""{"episode_number":1,"name":"1. Bölüm","still_path":"/s1.jpg","runtime":52}"""),
            obj("""{"episode_number":2,"name":"Bölüm 2","still_path":null,"runtime":null}"""),
        )
        val en = listOf(
            obj("""{"episode_number":1,"name":"Infected","still_path":"/s1_en.jpg","runtime":53}"""),
            obj("""{"episode_number":2,"name":"Episode 2","still_path":"/s2_en.jpg","runtime":59}"""),
        )
        val parsed = parseTmdbSeasonEpisodes(tr, en)

        assertEquals("Infected", parsed[1]?.name)
        assertEquals("${Tmdb.IMAGE_BASE}/w300/s1.jpg", parsed[1]?.still)
        assertEquals(52, parsed[1]?.runtimeMins)

        assertNull(parsed[2]?.name)
        assertEquals("${Tmdb.IMAGE_BASE}/w300/s2_en.jpg", parsed[2]?.still)
        assertEquals(59, parsed[2]?.runtimeMins)
    }

    @Test
    fun `missing still yields null without failing`() {
        val tr = listOf(
            obj("""{"episode_number":3,"name":"Long, Long Time"}"""),
        )
        val parsed = parseTmdbSeasonEpisodes(tr)
        assertEquals("Long, Long Time", parsed[3]?.name)
        assertNull(parsed[3]?.still)
        assertNull(parsed[3]?.runtimeMins)
    }
}
