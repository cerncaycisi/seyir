package dev.iptv.player.record

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StreamRecorderTest {
    @get:Rule val temp = TemporaryFolder()

    @Test(timeout = 15_000) fun failedSegmentIsRetriedWithoutAppendingPartialBytes() {
        MockWebServer().use { server ->
            val playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXTINF:1,\na.ts\n#EXTINF:1,\nb.ts\n#EXT-X-ENDLIST"
            server.enqueue(MockResponse().setBody(playlist))
            server.enqueue(MockResponse().setBody("x".repeat(100_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            server.enqueue(MockResponse().setBody(playlist))
            server.enqueue(MockResponse().setBody("complete-first-segment"))
            server.enqueue(MockResponse().setBody("complete-second-segment"))
            server.start()
            val output = File(temp.root, "recording.ts")
            StreamRecorder(StreamRecorder.newClient("test"), server.url("/index.m3u8").toString(), emptyMap(), output).run()
            assertEquals("complete-first-segmentcomplete-second-segment", output.readText())
            assertEquals(5, server.requestCount)
            assertFalse(temp.root.listFiles().orEmpty().any { it.extension == "part" })
        }
    }

    @Test(timeout = 10_000) fun unsupportedByteRangeDoesNotDownloadTheEntireMediaFile() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-BYTERANGE:100@0\n#EXTINF:1,\nall.ts\n#EXT-X-ENDLIST"))
            server.start()
            val output = File(temp.root, "range.ts")
            val error = runCatching {
                StreamRecorder(StreamRecorder.newClient("test"), server.url("/index.m3u8").toString(), emptyMap(), output).run()
            }.exceptionOrNull()
            assertTrue(error is RecorderException)
            assertEquals(RecorderError.UNSUPPORTED_HLS, (error as RecorderException).reason)
            assertEquals(1, server.requestCount)
            assertEquals(0, output.length())
        }
    }
}
