package com.fixermediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile

/**
 * Кэш всех фото/видео в MediaStore для быстрого сопоставления с файлами из SAF-папки.
 */
class MediaStoreIndex private constructor(
    private val byPathAndName: Map<PathNameKey, List<IndexedMedia>>,
    private val byName: Map<String, List<IndexedMedia>>
) {

    data class IndexedMedia(
        val uri: Uri,
        val displayName: String,
        val size: Long,
        val relativePath: String?,
        val dateModifiedSec: Long
    )

    data class PathNameKey(val relativePath: String, val displayName: String)

    fun lookup(entry: ScanEntry, documentSize: Long): Uri? {
        val name = entry.displayName
        val nameKey = name.lowercase()

        val pathHint = entry.relativePathHint
            ?: DocumentPathParser.relativePathFromDocumentUri(entry.documentUri)

        if (!pathHint.isNullOrBlank()) {
            val normalized = DocumentPathParser.normalizeRelativePath(pathHint)
            val pathMatches = byPathAndName[PathNameKey(normalized, name)]
                ?: byPathAndName[PathNameKey(normalized, nameKey)]
            pickBest(pathMatches, documentSize)?.let { return it.uri }
        }

        val nameMatches = byName[nameKey] ?: byName[name]
        return pickBest(nameMatches, documentSize)?.uri
    }

    private fun pickBest(candidates: List<IndexedMedia>?, documentSize: Long): IndexedMedia? {
        if (candidates.isNullOrEmpty()) return null
        if (candidates.size == 1) return candidates[0]

        if (documentSize > 0L) {
            val bySize = candidates.filter { it.size == documentSize }
            if (bySize.size == 1) return bySize[0]
            if (bySize.isNotEmpty()) {
                return bySize.maxByOrNull { it.dateModifiedSec }
            }
        }

        return candidates.maxByOrNull { it.dateModifiedSec }
    }

    companion object {
        @Volatile
        private var cached: MediaStoreIndex? = null

        fun getOrBuild(context: Context, forceRebuild: Boolean = false): MediaStoreIndex {
            if (!forceRebuild) {
                cached?.let { return it }
            }
            val built = build(context.applicationContext)
            cached = built
            return built
        }

        fun invalidate() {
            cached = null
        }

        fun build(context: Context): MediaStoreIndex {
            val byPath = mutableMapOf<PathNameKey, MutableList<IndexedMedia>>()
            val byName = mutableMapOf<String, MutableList<IndexedMedia>>()

            fun indexItem(item: IndexedMedia) {
                val nameKey = item.displayName.lowercase()
                byName.getOrPut(nameKey) { mutableListOf() }.add(item)
                val rel = item.relativePath
                if (!rel.isNullOrBlank()) {
                    val key = PathNameKey(rel, item.displayName)
                    byPath.getOrPut(key) { mutableListOf() }.add(item)
                    if (item.displayName != nameKey) {
                        byPath.getOrPut(PathNameKey(rel, nameKey)) { mutableListOf() }.add(item)
                    }
                }
            }

            for (volume in externalVolumes(context)) {
                indexVolume(context, volume, ::indexItem)
            }

            return MediaStoreIndex(byPath, byName)
        }

        private fun externalVolumes(context: Context): List<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.getExternalVolumeNames(context).toList()
            } else {
                listOf(MediaStore.VOLUME_EXTERNAL)
            }
        }

        private fun indexVolume(context: Context, volume: String, onIndexed: (IndexedMedia) -> Unit) {
            val collection = MediaStore.Files.getContentUri(volume)
            val projection = buildList {
                add(MediaStore.Files.FileColumns._ID)
                add(MediaStore.Files.FileColumns.DISPLAY_NAME)
                add(MediaStore.Files.FileColumns.SIZE)
                add(MediaStore.Files.FileColumns.DATE_MODIFIED)
                add(MediaStore.Files.FileColumns.MEDIA_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Files.FileColumns.RELATIVE_PATH)
                }
            }.toTypedArray()

            val selection =
                "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )

            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val size = cursor.getLong(sizeCol)
                    val modified = cursor.getLong(modCol)
                    val rel = if (pathCol >= 0) {
                        cursor.getString(pathCol)?.let { DocumentPathParser.normalizeRelativePath(it) }
                    } else {
                        null
                    }
                    onIndexed(
                        IndexedMedia(
                            uri = uri,
                            displayName = name,
                            size = size,
                            relativePath = rel,
                            dateModifiedSec = modified
                        )
                    )
                }
            }
        }
    }
}

object MediaStoreResolver {

    fun resolve(context: Context, entry: ScanEntry, index: MediaStoreIndex? = null): Uri? {
        entry.mediaStoreUri?.let { return it }

        val idx = index ?: MediaStoreIndex.getOrBuild(context)
        val docSize = readDocumentSize(context, entry.documentUri)
        return idx.lookup(entry, docSize)
    }

    fun readDocumentSize(context: Context, documentUri: Uri): Long {
        return try {
            DocumentFile.fromSingleUri(context, documentUri)?.length()?.takeIf { it > 0L }
                ?: context.contentResolver.openFileDescriptor(documentUri, "r")?.use { it.statSize }
                ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
