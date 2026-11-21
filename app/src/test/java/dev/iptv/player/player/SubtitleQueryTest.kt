package dev.iptv.player.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleQueryTest {
    @Test fun stripsPrefixTagsAndYear() {
        assertEquals("Film Adi" to "2019", cleanSubtitleQuery("TR: Film Adi (2019) 1080p"))
    }

    @Test fun trailingYear() {
        assertEquals("Dune" to "2021", cleanSubtitleQuery("Dune 2021"))
    }

    @Test fun plainTitle() {
        assertEquals("Some Show" to null, cleanSubtitleQuery("Some.Show.S01E02.WEB-DL"))
    }
}
