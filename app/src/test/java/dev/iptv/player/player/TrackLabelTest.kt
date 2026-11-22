package dev.iptv.player.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackLabelTest {
    private fun option(label: String, mime: String, sdh: Boolean = false): TrackOption {
        val f = Format.Builder().setSampleMimeType(mime).setLanguage("tr")
            .setRoleFlags(if (sdh) C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND else 0).build()
        val g = Tracks.Group(TrackGroup(f), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        return TrackOption(label, false, g, 0)
    }

    private fun labels(vararg o: TrackOption) = o.toMutableList().also(::disambiguate).map { it.label }

    @Test fun uniqueLabelsUntouched() =
        assertEquals(listOf("Türkçe", "İngilizce"), labels(option("Türkçe", MimeTypes.APPLICATION_SUBRIP), option("İngilizce", MimeTypes.APPLICATION_SUBRIP)))

    @Test fun sdhSetsApart() =
        assertEquals(listOf("Türkçe", "Türkçe · SDH"), labels(option("Türkçe", MimeTypes.APPLICATION_SUBRIP), option("Türkçe", MimeTypes.APPLICATION_SUBRIP, sdh = true)))

    @Test fun formatWhenItDiffers() =
        assertEquals(listOf("Türkçe · SRT", "Türkçe · PGS"), labels(option("Türkçe", MimeTypes.APPLICATION_SUBRIP), option("Türkçe", MimeTypes.APPLICATION_PGS)))

    @Test fun numberAsLastResort() =
        assertEquals(listOf("Türkçe · #1", "Türkçe · #2"), labels(option("Türkçe", MimeTypes.APPLICATION_SUBRIP), option("Türkçe", MimeTypes.APPLICATION_SUBRIP)))
}
