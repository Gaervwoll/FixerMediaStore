package com.fixermediastore

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParseResult(
    val dateTime: LocalDateTime,
    val patternLabel: String
)

/**
 * Извлекает дату/время из имени файла по распространённым шаблонам.
 * Первое совпадение побеждает.
 */
object FilenameDateParser {

    private data class PatternSpec(
        val label: String,
        val regex: Regex,
        val builder: (MatchResult) -> LocalDateTime?
    )

    private val patterns = listOf(
        PatternSpec("IMG_yyyyMMdd_HHmmss", Regex("""IMG_(\d{8})_(\d{6})""", RegexOption.IGNORE_CASE)) { m ->
            parseYmdHms(m.groupValues[1], m.groupValues[2])
        },
        PatternSpec("VID_yyyyMMdd_HHmmss", Regex("""VID_(\d{8})_(\d{6})""", RegexOption.IGNORE_CASE)) { m ->
            parseYmdHms(m.groupValues[1], m.groupValues[2])
        },
        PatternSpec("PXL_WP_MVIMG", Regex("""(?:PXL|WP|MVIMG)_(\d{8})_(\d{6})""", RegexOption.IGNORE_CASE)) { m ->
            parseYmdHms(m.groupValues[1], m.groupValues[2])
        },
        PatternSpec(
            "Screenshot",
            Regex("""Screenshot_(\d{4})-(\d{2})-(\d{2})-(\d{2})-(\d{2})-(\d{2})""", RegexOption.IGNORE_CASE)
        ) { m ->
            safeDateTime(m, 1, 2, 3, 4, 5, 6)
        },
        PatternSpec("WA_IMG_seq", Regex("""IMG-(\d{8})-WA\d*""", RegexOption.IGNORE_CASE)) { m ->
            parseYmdHms(m.groupValues[1], "120000")
        },
        PatternSpec(
            "Telegram_photo",
            Regex("""photo_(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})-(\d{2})""", RegexOption.IGNORE_CASE)
        ) { m ->
            safeDateTime(m, 1, 2, 3, 4, 5, 6)
        },
        PatternSpec(
            "Viber_image",
            Regex("""viber_image_(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})-(\d{2})""", RegexOption.IGNORE_CASE)
        ) { m ->
            safeDateTime(m, 1, 2, 3, 4, 5, 6)
        },
        PatternSpec(
            "Signal",
            Regex("""signal-(\d{4})-(\d{2})-(\d{2})-(\d{6})""", RegexOption.IGNORE_CASE)
        ) { m ->
            parseYmdHms(
                "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}",
                m.groupValues[4]
            )
        },
        PatternSpec("FB_IMG_ms", Regex("""FB_IMG_(\d{13})""", RegexOption.IGNORE_CASE)) { m ->
            fromEpochMillis(m.groupValues[1].toLongOrNull())
        },
        PatternSpec("WeChat_mmexport", Regex("""mmexport(\d{13})""", RegexOption.IGNORE_CASE)) { m ->
            fromEpochMillis(m.groupValues[1].toLongOrNull())
        },
        PatternSpec("yyyyMMdd_HHmmss_seq", Regex("""(\d{8})_(\d{6})(?:_\d+)?""")) { m ->
            parseYmdHms(m.groupValues[1], m.groupValues[2])
        },
        PatternSpec(
            "yyyy-MM-dd HH.mm.ss",
            Regex("""(\d{4})-(\d{2})-(\d{2})[ _](\d{2})\.(\d{2})\.(\d{2})""")
        ) { m ->
            safeDateTime(m, 1, 2, 3, 4, 5, 6)
        },
        PatternSpec("yyyy-MM-dd_HH-mm-ss", Regex("""(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})-(\d{2})""")) { m ->
            safeDateTime(m, 1, 2, 3, 4, 5, 6)
        },
        PatternSpec("yyyy-MM-dd_HHmmss", Regex("""(\d{4})-(\d{2})-(\d{2})_(\d{6})""")) { m ->
            parseYmdHms("${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}", m.groupValues[4])
        },
        PatternSpec("yyyyMMdd-HHmmss", Regex("""(\d{8})-(\d{6})""")) { m ->
            parseYmdHms(m.groupValues[1], m.groupValues[2])
        },
        PatternSpec("IMG-WA", Regex("""IMG-(\d{8})-WA""", RegexOption.IGNORE_CASE)) { m ->
            parseYmdHms(m.groupValues[1], "120000")
        },
        PatternSpec(
            "dd.MM.yyyy_HH-mm-ss",
            Regex("""(\d{2})\.(\d{2})\.(\d{4})_(\d{2})-(\d{2})-(\d{2})""")
        ) { m ->
            safeDateTime(m, 3, 2, 1, 4, 5, 6)
        },
        PatternSpec("yyyy-MM-dd", Regex("""(\d{4})-(\d{2})-(\d{2})(?:[^\d]|$)""")) { m ->
            safeDateTime(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt(),
                12,
                0,
                0
            )
        },
        PatternSpec("unix_seconds", Regex("""(?:^|[_\-.])(\d{10})(?:[_\-.]|\.|$)""")) { m ->
            val epochSec = m.groupValues[1].toLongOrNull() ?: return@PatternSpec null
            fromEpochSeconds(epochSec)
        },
        PatternSpec("yyyyMMdd", Regex("""(\d{8})""")) { m ->
            parseYmdHms(m.groupValues[1], "120000")
        }
    )

    private val exifFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    private const val MIN_EPOCH_MS = 946_684_800_000L // 2000-01-01
    private const val MAX_EPOCH_MS = 4_102_444_800_000L // ~2100-01-01

    fun parse(filename: String): LocalDateTime? = parseWithLabel(filename)?.dateTime

    fun parseWithLabel(filename: String): ParseResult? {
        val name = filename.substringBeforeLast('.')
        for (spec in patterns) {
            val match = spec.regex.find(name) ?: continue
            val result = spec.builder(match) ?: continue
            return ParseResult(result, spec.label)
        }
        return null
    }

    fun toExifString(dateTime: LocalDateTime): String = dateTime.format(exifFormatter)

    fun toEpochMillis(dateTime: LocalDateTime): Long {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun fromEpochMillis(ms: Long?): LocalDateTime? {
        if (ms == null || ms !in MIN_EPOCH_MS..MAX_EPOCH_MS) return null
        return try {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
        } catch (_: Exception) {
            null
        }
    }

    private fun fromEpochSeconds(sec: Long): LocalDateTime? {
        return fromEpochMillis(sec * 1000L)
    }

    private fun parseYmdHms(ymd: String, hms: String): LocalDateTime? {
        if (ymd.length != 8 || hms.length != 6) return null
        return safeDateTime(
            ymd.substring(0, 4).toInt(),
            ymd.substring(4, 6).toInt(),
            ymd.substring(6, 8).toInt(),
            hms.substring(0, 2).toInt(),
            hms.substring(2, 4).toInt(),
            hms.substring(4, 6).toInt()
        )
    }

    private fun safeDateTime(
        match: MatchResult,
        yearIdx: Int,
        monthIdx: Int,
        dayIdx: Int,
        hourIdx: Int,
        minuteIdx: Int,
        secondIdx: Int
    ): LocalDateTime? {
        return safeDateTime(
            match.groupValues[yearIdx].toInt(),
            match.groupValues[monthIdx].toInt(),
            match.groupValues[dayIdx].toInt(),
            match.groupValues[hourIdx].toInt(),
            match.groupValues[minuteIdx].toInt(),
            match.groupValues[secondIdx].toInt()
        )
    }

    private fun safeDateTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): LocalDateTime? {
        return try {
            LocalDateTime.of(
                LocalDate.of(year, month, day),
                LocalTime.of(hour, minute, second)
            )
        } catch (_: Exception) {
            null
        }
    }
}
