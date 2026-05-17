package com.fixermediastore

import android.content.Context
import android.net.Uri
import java.time.LocalDateTime

data class ResolvedDate(
    val dateTime: LocalDateTime,
    val patternLabel: String,
    val dateSource: String
)

object DateResolver {

    const val SOURCE_FILENAME = "filename"
    const val SOURCE_EXIF = "exif"

    fun resolve(
        context: Context,
        displayName: String,
        uri: Uri,
        useExifWhenNoFilename: Boolean
    ): ResolvedDate? {
        FilenameDateParser.parseWithLabel(displayName)?.let { parsed ->
            return ResolvedDate(parsed.dateTime, parsed.patternLabel, SOURCE_FILENAME)
        }

        if (!useExifWhenNoFilename) return null

        val fromExif = ExifDateReader.readDateTime(context, uri, displayName) ?: return null
        return ResolvedDate(fromExif, "EXIF", SOURCE_EXIF)
    }
}
