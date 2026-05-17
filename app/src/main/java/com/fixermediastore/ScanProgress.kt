package com.fixermediastore

fun interface ScanProgressListener {
    /** @param processed сколько уже обработано; @param total всего (0 = неизвестно) */
    fun onProgress(processed: Int, total: Int, currentName: String?)
}
