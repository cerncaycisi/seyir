package dev.iptv.player.data.tmdb

import dev.iptv.player.data.ContentType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TmdbMatchingTest {
    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tempDir = Files.createTempDirectory("tmdb-test").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun tmdb() = Tmdb(
        oldDir = null,
        dir = tempDir,
        enabled = { true },
        apiKey = "test-key",
        baseUrl = server.url("/").toString().trimEnd('/'),
    )

    @Test
    fun runtimeMatchesBoundaries() {
        assertTrue(runtimeMatches(null, 94))
        assertTrue(runtimeMatches(5640, null))
        assertTrue(runtimeMatches(0, 94))
        assertTrue(runtimeMatches(5640, 0))
        assertTrue(runtimeMatches(5640, 94))
        assertTrue(runtimeMatches(5640, 86))
        assertFalse(runtimeMatches(5640, 74))
        assertTrue(runtimeMatches(150 * 60, 135))
        assertFalse(runtimeMatches(150 * 60, 110))
    }

    @Test
    fun directTmdbIdLookupUsesTvEndpointAndParsesDetails() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return if (path == "/tv/58887") {
                    MockResponse().setBody(
                        """{"id":58887,"name":"Hz. Ömer","overview":"Ömer b. Hattab","poster_path":"/p.jpg","backdrop_path":"/b.jpg","first_air_date":"2012-07-20","vote_average":9.0,"genres":[{"id":18,"name":"Dram"}],"original_language":"ar","spoken_languages":[{"iso_639_1":"ar"}]}""",
                    )
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }
        val res = tmdb().resolve("Farouk Omar", ContentType.SERIES, tmdbId = 58887L)
        assertTrue(res.completed)
        val info = res.info
        assertNotNull(info)
        assertEquals(58887L, info!!.tmdbId)
        assertEquals("Hz. Ömer", info.title)
        assertEquals("Dram", info.genre)
        assertEquals("ar", info.originalLanguage)
        assertEquals("2012", info.year)
    }

    @Test
    fun missing404ProviderTmdbIdFallsBackToTitleSearch() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when (path) {
                    "/movie/999999" -> MockResponse().setResponseCode(404)
                    "/search/movie" -> MockResponse().setBody(
                        """{"results":[{"id":10,"title":"The Batman","overview":"Detay","release_date":"2022-03-01","vote_average":7.7,"genre_ids":[28]}]}""",
                    )
                    "/genre/movie/list" -> MockResponse().setBody("""{"genres":[{"id":28,"name":"Aksiyon"}]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val res = tmdb().resolve("The Batman", ContentType.MOVIE, yearHint = "2022", tmdbId = 999999L)
        assertTrue(res.completed)
        assertEquals(10L, res.info?.tmdbId)
        assertEquals("Aksiyon", res.info?.genre)
    }

    @Test
    fun durationDisambiguationRejectsShortDocAndPicksMatchingYearlessCandidate() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/search/movie" -> {
                        if (url.queryParameter("year") == "2021") {
                            MockResponse().setBody(
                                """{"results":[{"id":957152,"title":"Göz Açıp Kapayıncaya Kadar","release_date":"2021-03-12"}]}""",
                            )
                        } else {
                            MockResponse().setBody(
                                """{"results":[{"id":1032892,"title":"Göz Açıp Kapayıncaya Kadar","original_title":"In the Blink of an Eye","release_date":"2026-01-26"},{"id":957152,"title":"Göz Açıp Kapayıncaya Kadar","release_date":"2021-03-12"}]}""",
                            )
                        }
                    }
                    "/movie/957152" -> MockResponse().setBody(
                        """{"id":957152,"title":"Göz Açıp Kapayıncaya Kadar","overview":"Belgesel","release_date":"2021-03-12","runtime":74,"original_language":"tr","spoken_languages":[{"iso_639_1":"tr"}],"genres":[]}""",
                    )
                    "/movie/1032892" -> MockResponse().setBody(
                        """{"id":1032892,"title":"Göz Açıp Kapayıncaya Kadar","overview":"Bilim kurgu","release_date":"2026-01-26","runtime":94,"original_language":"en","spoken_languages":[{"iso_639_1":"en"}],"genres":[{"id":878,"name":"Bilim-Kurgu"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val res = tmdb().resolve(
            "Göz Açıp Kapayıncaya Kadar (2021)",
            ContentType.MOVIE,
            yearHint = "2021",
            durationSecs = 5640,
        )
        assertTrue(res.completed)
        val info = res.info
        assertNotNull(info)
        assertEquals(1032892L, info!!.tmdbId)
        assertEquals("2026", info.year)
        assertEquals("en", info.originalLanguage)
        assertEquals(94, info.runtimeMins)
    }

    @Test
    fun mismatchedProviderTmdbIdIsOverriddenByActualDuration() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/search/movie" -> {
                        if (url.queryParameter("year") == "2021") {
                            MockResponse().setBody("""{"results":[{"id":957152,"title":"Göz Açıp Kapayıncaya Kadar","release_date":"2021-03-12"}]}""")
                        } else {
                            MockResponse().setBody("""{"results":[{"id":1032892,"title":"Göz Açıp Kapayıncaya Kadar","original_title":"In the Blink of an Eye","release_date":"2026-01-26"},{"id":957152,"title":"Göz Açıp Kapayıncaya Kadar","release_date":"2021-03-12"}]}""")
                        }
                    }
                    "/movie/957152" -> MockResponse().setBody(
                        """{"id":957152,"title":"Göz Açıp Kapayıncaya Kadar","overview":"Belgesel","release_date":"2021-03-12","runtime":74,"original_language":"tr","spoken_languages":[{"iso_639_1":"tr"}],"genres":[]}""",
                    )
                    "/movie/1032892" -> MockResponse().setBody(
                        """{"id":1032892,"title":"Göz Açıp Kapayıncaya Kadar","overview":"Bilim kurgu","release_date":"2026-01-26","runtime":94,"original_language":"en","spoken_languages":[{"iso_639_1":"en"}],"genres":[{"id":878,"name":"Bilim-Kurgu"}]}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val res = tmdb().resolve(
            "Göz Açıp Kapayıncaya Kadar (2021)",
            ContentType.MOVIE,
            yearHint = "2021",
            tmdbId = 957152L,
            durationSecs = 5640,
        )
        assertTrue(res.completed)
        assertEquals(1032892L, res.info?.tmdbId)
        assertEquals("2026", res.info?.year)
        assertEquals("en", res.info?.originalLanguage)
    }
}
