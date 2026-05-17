package com.fixermediastore

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object MediaFileScanner {

    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "heic", "heif",
        "mp4", "mov", "mkv", "3gp", "webm"
    )

    fun scan(
        context: Context,
        treeUri: Uri,
        useExifWhenNoFilename: Boolean,
        onProgress: ScanProgressListener? = null
    ): List<ScanEntry> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        onProgress?.onProgress(0, 0, null)
        val total = countMediaFiles(root)
        val results = mutableListOf<ScanEntry>()
        var processed = 0
        walk(context, root, results, useExifWhenNoFilename) { name ->
            processed++
            onProgress?.onProgress(processed, total, name)
        }
        return results
    }

    private fun countMediaFiles(file: DocumentFile): Int {
        if (file.isDirectory) {
            return file.listFiles().sumOf { child -> countMediaFiles(child) }
        }
        if (!file.isFile) return 0
        val name = file.name ?: return 0
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext in mediaExtensions) 1 else 0
    }

    private fun walk(
        context: Context,
        file: DocumentFile,
        out: MutableList<ScanEntry>,
        useExifWhenNoFilename: Boolean,
        onFile: (String) -> Unit
    ) {
        if (file.isDirectory) {
            file.listFiles().forEach { child ->
                walk(context, child, out, useExifWhenNoFilename, onFile)
            }
            return
        }
        if (!file.isFile) return

        val name = file.name ?: return
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in mediaExtensions) return

        val pathHint = DocumentPathParser.relativePathFromDocumentUri(file.uri)
        out += entryFromResolved(
            context,
            name,
            file.uri,
            mediaStoreUri = null,
            relativePathHint = pathHint,
            useExifWhenNoFilename = useExifWhenNoFilename
        )
        onFile(name)
    }

    fun entryFromResolved(
        context: Context,
        name: String,
        uri: Uri,
        mediaStoreUri: Uri?,
        relativePathHint: String? = null,
        useExifWhenNoFilename: Boolean = true
    ): ScanEntry {
        val resolved = DateResolver.resolve(context, name, uri, useExifWhenNoFilename)
        val status = if (resolved != null) ScanStatus.READY else ScanStatus.SKIPPED_NO_DATE
        val message = when {
            resolved != null -> "${resolved.patternLabel}: ${resolved.dateTime}"
            useExifWhenNoFilename && ExifDateReader.canReadExif(name) ->
                context.getString(R.string.msg_no_date_in_name_or_exif)
            else -> context.getString(R.string.msg_no_date_in_name)
        }
        return ScanEntry(
            displayName = name,
            documentUri = uri,
            parsedDateTime = resolved?.dateTime,
            mediaStoreUri = mediaStoreUri,
            relativePathHint = relativePathHint,
            matchedPattern = resolved?.patternLabel,
            dateSource = resolved?.dateSource,
            status = status,
            message = message
        )
    }
}
