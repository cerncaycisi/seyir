package dev.iptv.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeparatorNameTest {
    @Test
    fun panelSeparatorsAreDetected() {
        assertTrue(ParentalFilter.isSeparatorName("∞∞∞∞ TURK ULUSAL ∞∞∞∞"))
        assertTrue(ParentalFilter.isSeparatorName("##### SPOR #####"))
        assertTrue(ParentalFilter.isSeparatorName("===== HABER ====="))
        assertFalse(ParentalFilter.isSeparatorName("TRT 1 HD"))
        assertFalse(ParentalFilter.isSeparatorName("COKLU EKRAN ULUSAL"))
        assertFalse(ParentalFilter.isSeparatorName("★ Star TV"))
    }
}
