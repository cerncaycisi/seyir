package dev.iptv.player.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class XtreamApiTest {
    private lateinit var server: MockWebServer
    private val responses = HashMap<String, String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action").orEmpty()
                val body = responses[action] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun api() = XtreamApi(
        PlaylistSource.Xtream(server.url("/").toString(), "test", "test"),
        OkHttpClient(),
    )

    private val base get() = server.url("/").toString().trimEnd('/')

    @Test
    fun loginAcceptsNumberOrStringAuth() = runBlocking {
        responses[""] = """{"user_info":{"auth":1,"status":"Active"}}"""
        assertTrue(api().login())
        responses[""] = """{"user_info":{"auth":"1"}}"""
        assertTrue(api().login())
        responses[""] = """{"user_info":{"auth":0}}"""
        assertFalse(api().login())
        responses[""] = """[]"""
        assertFalse(api().login())
    }

    @Test
    fun accountInfoParsesMixedTypes() = runBlocking {
        responses[""] = """{"user_info":{"auth":1,"status":"Active","exp_date":"1767225600","max_connections":"2","active_cons":1,"is_trial":"0"}}"""
        val info = api().accountInfo()!!
        assertEquals("Active", info.status)
        assertEquals(1767225600L, info.expiresAt)
        assertEquals(2, info.maxConnections)
        assertEquals(1, info.activeConnections)
        assertFalse(info.trial)

        responses[""] = """{"user_info":{"auth":1,"exp_date":null,"max_connections":null,"is_trial":1}}"""
        val unlimited = api().accountInfo()!!
        assertNull(unlimited.expiresAt)
        assertNull(unlimited.maxConnections)
        assertTrue(unlimited.trial)
    }

    @Test
    fun categoriesWithNumericIdsAndNullNames() = runBlocking {
        responses["get_live_categories"] = """[{"category_id":1,"category_name":"Spor"},{"category_id":"2","category_name":null},{"category_name":"no id"},"junk"]"""
        val cats = api().categories(ContentType.LIVE)
        assertEquals(listOf(Category("1", "Spor"), Category("2", "2")), cats)
    }

    @Test
    fun liveStreamsHandleNullsAndCatchup() = runBlocking {
        responses["get_live_streams"] = """[
            {"stream_id":10,"name":"TRT 1","stream_icon":"","category_id":1,"epg_channel_id":"trt1","tv_archive":1,"tv_archive_duration":"3"},
            {"stream_id":"11","name":null,"stream_icon":null,"category_id":null,"epg_channel_id":null,"tv_archive":0},
            {"name":"no id"}]"""
        val items = api().streams(ContentType.LIVE)
        assertEquals(2, items.size)
        assertEquals("$base/live/test/test/10.ts", items[0].url)
        assertNull(items[0].logo)
        assertEquals("1", items[0].categoryId)
        assertEquals("trt1", items[0].epgId)
        assertEquals("xc", items[0].catchup)
        assertEquals(3, items[0].catchupDays)
        assertEquals("", items[1].name)
        assertEquals("", items[1].categoryId)
        assertNull(items[1].epgId)
        assertNull(items[1].catchup)
    }

    @Test
    fun vodStreamsHandleExtensionAndRating() = runBlocking {
        responses["get_vod_streams"] = """[
            {"stream_id":1,"name":"A","container_extension":"mkv","rating":"7.456","year":"2020","category_id":"5","tmdb_id":1032892},
            {"stream_id":"2","name":"B","container_extension":null,"rating":0,"category_id":5,"tmdb":"0"},
            {"stream_id":3,"name":"C","rating":7.5,"rating_5based":3.5,"tmdb":"4242"}]"""
        val items = api().streams(ContentType.MOVIE)
        assertEquals("$base/movie/test/test/1.mkv", items[0].url)
        assertEquals("7.5", items[0].rating)
        assertEquals(1032892L, items[0].tmdbId)
        assertEquals("$base/movie/test/test/2.mp4", items[1].url)
        assertNull(items[1].rating)
        assertNull(items[1].tmdbId)
        assertEquals("7.5", items[2].rating)
        assertEquals(4242L, items[2].tmdbId)
    }

    @Test
    fun seriesListHandlesBackdropArrayAndStringIds() = runBlocking {
        responses["get_series"] = """[
            {"series_id":7,"name":"S","cover":"c.jpg","backdrop_path":["b1.jpg","b2.jpg"],"releaseDate":"2019-05-01","category_id":"3","tmdb_id":"58887"},
            {"series_id":"8","name":"T","backdrop_path":"b.jpg","release_date":null,"category_id":3,"tmdb":""}]"""
        val items = api().streams(ContentType.SERIES)
        assertEquals("7", items[0].id)
        assertEquals("b1.jpg", items[0].backdrop)
        assertEquals("2019", items[0].year)
        assertEquals(58887L, items[0].tmdbId)
        assertEquals("b.jpg", items[1].backdrop)
        assertNull(items[1].year)
        assertNull(items[1].tmdbId)
    }

    private fun ep(id: Any, season: Any, num: Any) =
        """{"id":$id,"episode_num":$num,"season":$season,"title":"E$num","container_extension":"mp4"}"""

    @Test
    fun seriesInfoEpisodesAsObjectBySeason() = runBlocking {
        responses["get_series_info"] =
            """{"info":{"name":"S","backdrop_path":[],"rating":"8","tmdb_id":58887},"episodes":{"1":[${ep(11, 1, 1)},${ep("12", "1", "2")}],"2":[${ep(21, 2, 1)}]}}"""
        val info = api().seriesInfo("7")
        assertEquals(listOf("11", "12", "21"), info.episodes.map { it.id })
        assertEquals(listOf(1, 1, 2), info.episodes.map { it.season })
        assertEquals("$base/series/test/test/11.mp4", info.episodes[0].url)
        assertNull(info.backdrop)
        assertEquals("8.0", info.rating)
        assertEquals(58887L, info.tmdbId)
    }

    @Test
    fun seriesInfoEpisodesAsNestedArray() = runBlocking {
        responses["get_series_info"] = """{"info":null,"episodes":[[${ep(11, 1, 2)},${ep(10, 1, 1)}],[${ep(21, 2, 1)}]]}"""
        val info = api().seriesInfo("7")
        assertEquals(listOf("10", "11", "21"), info.episodes.map { it.id })
        assertEquals("", info.name)
    }

    @Test
    fun seriesInfoEmptyEpisodesAsEmptyArrayOrNull() = runBlocking {
        responses["get_series_info"] = """{"info":{},"episodes":[]}"""
        assertTrue(api().seriesInfo("7").episodes.isEmpty())
        responses["get_series_info"] = """{"info":{},"episodes":null}"""
        assertTrue(api().seriesInfo("7").episodes.isEmpty())
    }

    @Test
    fun vodInfoToleratesNullsAndArrays() = runBlocking {
        responses["get_vod_info"] = """{"info":{"plot":null,"description":"d","backdrop_path":["x.jpg"],"genre":"","duration":"01:30:00","releasedate":"2021-02-03","rating":"6","tmdb_id":null},"movie_data":{"tmdb_id":"1032892"}}"""
        val info = api().vodInfo("1")!!
        assertEquals("d", info.plot)
        assertEquals("x.jpg", info.backdrop)
        assertNull(info.genre)
        assertEquals("2021", info.year)
        assertEquals(1032892L, info.tmdbId)
        responses["get_vod_info"] = """[]"""
        assertNull(api().vodInfo("1"))
    }

    @Test
    fun httpErrorThrowsIOException() {
        val failure = runCatching { runBlocking { api().categories(ContentType.LIVE) } }.exceptionOrNull()
        assertTrue(failure is IOException)
    }

    @Test
    fun shortEpgParsesTimestampsAsStringsOrNumbers() = runBlocking {
        responses["get_short_epg"] = """{"epg_listings":[{"title":"VGl0bGU=","start_timestamp":"100","stop_timestamp":200},{"title":"x","start_timestamp":null,"stop_timestamp":5}]}"""
        val epg = api().shortEpg("1")
        assertEquals(1, epg.size)
        assertEquals(100L, epg[0].start)
        assertEquals(200L, epg[0].end)
    }
}
