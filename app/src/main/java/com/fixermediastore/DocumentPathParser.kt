package com.fixermediastore

import android.net.Uri
import android.provider.DocumentsContract

object DocumentPathParser {

    /**
     * Из document URI (SAF) извлекает относительный путь папки в формате MediaStore, например `DCIM/Camera/`.
     */
    fun relativePathFromDocumentUri(uri: Uri): String? {
        if (uri.authority?.contains("documents") != true) return null
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: Exception) {
            return null
        }
        return relativePathFromDocumentId(docId)
    }

    fun relativePathFromDocumentId(documentId: String): String? {
        val colon = documentId.indexOf(':')
        if (colon < 0) return null
        val pathPart = documentId.substring(colon + 1)
        val lastSlash = pathPart.lastIndexOf('/')
        if (lastSlash < 0) return null
        val dir = pathPart.substring(0, lastSlash + 1)
        return normalizeRelativePath(dir)
    }

    fun normalizeRelativePath(path: String): String {
        var p = path.replace('\\', '/').trimStart('/')
        if (p.isEmpty()) return ""
        if (!p.endsWith('/')) p += "/"
        return p
    }
}
