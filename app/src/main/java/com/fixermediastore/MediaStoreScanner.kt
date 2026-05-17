package com.fixermediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MediaStoreScanner {

    fun scan(
        context: Context,
        useExifWhenNoFilename: Boolean,
        onProgress: ScanProgressListener? = null
    ): List<ScanEntry> {
        val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videosUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val imageTotal = countCollection(context, imagesUri)
        val videoTotal = countCollection(context, videosUri)
        val grandTotal = imageTotal + videoTotal

        val results = mutableListOf<ScanEntry>()
        results += scanCollection(
            context,
            imagesUri,
            useExifWhenNoFilename,
            onProgress,
            processedOffset = 0,
            grandTotal = grandTotal
        )
        results += scanCollection(
            context,
            videosUri,
            useExifWhenNoFilename,
            onProgress,
            processedOffset = imageTotal,
            grandTotal = grandTotal
        )
        return results
    }

    private fun countCollection(context: Context, collection: Uri): Int {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            return cursor.count.coerceAtLeast(0)
        }
        return 0
    }

    private fun scanCollection(
        context: Context,
        collection: Uri,
        useExifWhenNoFilename: Boolean,
        onProgress: ScanProgressListener?,
        processedOffset: Int,
        grandTotal: Int
    ): List<ScanEntry> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val entries = mutableListOf<ScanEntry>()
        var processed = 0

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                entries += MediaFileScanner.entryFromResolved(
                    context = context,
                    name = name,
                    uri = uri,
                    mediaStoreUri = uri,
                    useExifWhenNoFilename = useExifWhenNoFilename
                )
                processed++
                onProgress?.onProgress(processedOffset + processed, grandTotal, name)
            }
        }
        return entries
    }
}
