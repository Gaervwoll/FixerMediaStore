package com.fixermediastore

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import kotlin.math.abs

object MetadataRestorer {

    /** Допуск при сравнении DATE_TAKEN с датой из имени (2 минуты). */
    const val DATE_MATCH_TOLERANCE_MS = 120_000L

    private val videoExtensions = setOf("mp4", "mov", "mkv", "3gp", "webm")
    private val jpegExtensions = setOf("jpg", "jpeg")

    fun resolveMediaUri(context: Context, entry: ScanEntry): Uri? {
        return MediaStoreResolver.resolve(context, entry)
    }

    fun readDateTakenMillis(context: Context, mediaUri: Uri): Long? {
        val projection = arrayOf(MediaStore.MediaColumns.DATE_TAKEN)
        context.contentResolver.query(mediaUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val value = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
            return if (value > 0L) value else null
        }
        return null
    }

    fun isDateAlreadyCorrect(
        context: Context,
        entry: ScanEntry,
        mediaUri: Uri? = null,
        toleranceMs: Long = DATE_MATCH_TOLERANCE_MS
    ): Boolean {
        val uri = mediaUri ?: resolveMediaUri(context, entry) ?: return false
        val target = entry.parsedDateTime ?: return false
        val targetMillis = FilenameDateParser.toEpochMillis(target)
        val current = readDateTakenMillis(context, uri) ?: return false
        return abs(current - targetMillis) <= toleranceMs
    }

    fun restore(context: Context, entry: ScanEntry): RestoreResult {
        if (entry.status == ScanStatus.APPLIED) {
            return RestoreResult.Skipped(entry, context.getString(R.string.msg_already_applied))
        }
        if (entry.status == ScanStatus.SKIPPED_ALREADY_CORRECT) {
            return RestoreResult.Skipped(entry, entry.message)
        }

        return try {
            val dateTime = entry.parsedDateTime
            if (dateTime == null) {
                entry.status = ScanStatus.SKIPPED_NO_DATE
                entry.message = context.getString(R.string.msg_no_date_in_name)
                RestoreResult.Failed(entry, entry.message)
            } else {
                val millis = FilenameDateParser.toEpochMillis(dateTime)
                val ext = entry.displayName.substringAfterLast('.', "").lowercase()

                val mediaUri = resolveMediaUri(context, entry)

                if (mediaUri == null) {
                    entry.status = ScanStatus.ERROR_NOT_IN_STORE
                    entry.message = context.getString(R.string.msg_not_in_store)
                    RestoreResult.Failed(entry, entry.message)
                } else {
                    entry.mediaStoreUri = mediaUri
                    if (isDateAlreadyCorrect(context, entry, mediaUri)) {
                        entry.status = ScanStatus.SKIPPED_ALREADY_CORRECT
                        entry.message = context.getString(R.string.msg_date_already_correct)
                        RestoreResult.Skipped(entry, entry.message)
                    } else {
                        applyUpdate(context, entry, mediaUri, millis, ext)
                        if (entry.status == ScanStatus.APPLIED) {
                            RestoreResult.Success(entry)
                        } else {
                            RestoreResult.Failed(entry, entry.message)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            entry.status = ScanStatus.ERROR_OTHER
            entry.message = "${e.javaClass.simpleName}: ${e.message ?: context.getString(R.string.msg_unknown)}"
            RestoreResult.Failed(entry, entry.message)
        }
    }

    private fun applyUpdate(
        context: Context,
        entry: ScanEntry,
        mediaUri: Uri,
        millis: Long,
        ext: String
    ) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATE_TAKEN, millis)
            put(MediaStore.MediaColumns.DATE_MODIFIED, millis / 1000)
        }

        val updated = context.contentResolver.update(mediaUri, values, null, null)
        if (updated <= 0) {
            entry.status = ScanStatus.ERROR_OTHER
            entry.message = context.getString(R.string.msg_media_store_update_failed)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val added = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATE_ADDED, millis / 1000)
                }
                context.contentResolver.update(mediaUri, added, null, null)
            } catch (_: Exception) {
            }
        }

        var exifNote = ""
        if (ext in jpegExtensions) {
            exifNote = try {
                updateExif(context, entry, entry.parsedDateTime!!)
                context.getString(R.string.msg_ok_exif_suffix)
            } catch (e: Exception) {
                context.getString(
                    R.string.msg_exif_failed,
                    e.message ?: e.javaClass.simpleName
                )
            }
        }

        entry.status = ScanStatus.APPLIED
        entry.message = context.getString(R.string.msg_ok) + exifNote
    }

    private fun updateExif(context: Context, entry: ScanEntry, dateTime: java.time.LocalDateTime) {
        val exifString = FilenameDateParser.toExifString(dateTime)
        val urisToTry = listOfNotNull(entry.mediaStoreUri, entry.documentUri).distinct()

        var lastError: Exception? = null
        for (uri in urisToTry) {
            try {
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifString)
                    exif.setAttribute(ExifInterface.TAG_DATETIME, exifString)
                    exif.saveAttributes()
                } ?: throw IOException("cannot open file for EXIF")
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("cannot open file for EXIF")
    }
}
