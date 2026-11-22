package dev.iptv.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleWithoutYearTest {
    @Test fun stripsTrailingYear() = assertEquals("Göz Açıp Kapayıncaya Kadar", titleWithoutYear("Göz Açıp Kapayıncaya Kadar (2021)", "2021"))
    @Test fun keepsOtherYears() = assertEquals("Blade Runner (2049)", titleWithoutYear("Blade Runner (2049)", "2017"))
    @Test fun keepsWithoutYear() = assertEquals("Teşkilat", titleWithoutYear("Teşkilat", null))
    @Test fun neverEmpty() = assertEquals("(2021)", titleWithoutYear("(2021)", "2021"))
}
