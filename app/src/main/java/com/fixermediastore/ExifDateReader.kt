package com.fixermediastore

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object ExifDateReader {

    private val exifFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    private val exifExtensions = setOf("jpg", "jpeg", "heic", "heif", "png", "webp")

    fun canReadExif(displayName: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return ext in exifExtensions
    }

    fun readDateTime(context: Context, uri: Uri, displayName: String): LocalDateTime? {
        if (!canReadExif(displayName)) return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                readFromExif(exif)
            }
        } catch (_: Exception) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    readFromExif(exif)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun readFromExif(exif: ExifInterface): LocalDateTime? {
        val tags = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED
        )
        for (tag in tags) {
            val raw = exif.getAttribute(tag) ?: continue
            parseExifString(raw)?.let { return it }
        }
        return null
    }

    private fun parseExifString(raw: String): LocalDateTime? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            LocalDateTime.parse(trimmed, exifFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
