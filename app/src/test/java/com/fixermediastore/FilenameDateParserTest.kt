package com.fixermediastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class FilenameDateParserTest {

    @Test
    fun parseImgPattern() {
        val result = FilenameDateParser.parse("IMG_20240515_143022.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseVidPattern() {
        val result = FilenameDateParser.parse("VID_20240515_143022.mp4")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parsePxlPattern() {
        val result = FilenameDateParser.parse("PXL_20240515_143022.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseScreenshotPattern() {
        val result = FilenameDateParser.parse("Screenshot_2024-05-15-14-30-22.png")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parsePlainYmdHms() {
        val result = FilenameDateParser.parse("20240515_143022.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseYmdHmsWithSequence() {
        val result = FilenameDateParser.parse("20240515_143022_001.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseDottedDateTime() {
        val result = FilenameDateParser.parse("2024-05-15 14.30.22.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseDashedDateTime() {
        val result = FilenameDateParser.parse("2024-05-15_14-30-22.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseIsoDateUnderscoreTime() {
        val result = FilenameDateParser.parse("2024-05-15_143022.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseYmdDashHms() {
        val result = FilenameDateParser.parse("20240515-143022.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseIsoDateOnly() {
        val result = FilenameDateParser.parse("2024-05-15.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 12, 0, 0), result)
    }

    @Test
    fun parseEuropeanPattern() {
        val result = FilenameDateParser.parse("15.05.2024_14-30-22.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseWhatsAppPattern() {
        val result = FilenameDateParser.parse("IMG-20240515-WA0001.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 12, 0, 0), result)
    }

    @Test
    fun parseTelegramPhotoPattern() {
        val result = FilenameDateParser.parse("photo_2024-05-15_14-30-22.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseFbImgMillis() {
        val result = FilenameDateParser.parse("FB_IMG_1715789420123.jpg")
        assertNotNull(result)
        assertEquals(2024, result!!.year)
    }

    @Test
    fun parseWeChatMmexport() {
        val result = FilenameDateParser.parse("mmexport1715789420123.jpg")
        assertNotNull(result)
    }

    @Test
    fun parseViberImage() {
        val result = FilenameDateParser.parse("viber_image_2024-05-15_14-30-22.jpg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseSignalPattern() {
        val result = FilenameDateParser.parse("signal-2024-05-15-143022.jpeg")
        assertEquals(LocalDateTime.of(2024, 5, 15, 14, 30, 22), result)
    }

    @Test
    fun parseUnixTimestamp() {
        val result = FilenameDateParser.parse("photo_1715775022.jpg")
        assertNotNull(result)
    }

    @Test
    fun parseDateOnlyDefaultsNoon() {
        val result = FilenameDateParser.parse("photo_20240515.png")
        assertEquals(LocalDateTime.of(2024, 5, 15, 12, 0, 0), result)
    }

    @Test
    fun parseWithLabelReturnsPatternName() {
        val result = FilenameDateParser.parseWithLabel("IMG_20240515_143022.jpg")
        assertNotNull(result)
        assertEquals("IMG_yyyyMMdd_HHmmss", result?.patternLabel)
    }

    @Test
    fun parseUnknownReturnsNull() {
        assertNull(FilenameDateParser.parse("vacation.jpg"))
    }

    @Test
    fun toExifStringFormat() {
        val dt = LocalDateTime.of(2024, 5, 15, 14, 30, 22)
        assertEquals("2024:05:15 14:30:22", FilenameDateParser.toExifString(dt))
    }
}
