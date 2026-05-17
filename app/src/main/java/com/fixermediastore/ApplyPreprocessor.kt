package com.fixermediastore

import android.content.Context

data class PreflightSummary(
    val stillReady: Int,
    val skippedCorrect: Int,
    val markedErrors: Int,
    val resolvedNow: Int
)

object ApplyPreprocessor {

    /**
     * Проверка до применения: строит индекс MediaStore, сопоставляет файлы, отсеивает уже правильные даты.
     */
    fun prepare(
        context: Context,
        entries: List<ScanEntry>,
        skipAlreadyCorrect: Boolean,
        retryPreviousErrors: Boolean = true
    ): PreflightSummary {
        var skippedCorrect = 0
        var markedErrors = 0
        var resolvedNow = 0

        val index = MediaStoreIndex.getOrBuild(context, forceRebuild = true)

        for (entry in entries) {
            if (entry.status == ScanStatus.ERROR_NOT_IN_STORE && retryPreviousErrors && entry.parsedDateTime != null) {
                entry.status = ScanStatus.READY
                entry.message = ""
            }
            if (entry.status != ScanStatus.READY) continue

            val mediaUri = MediaStoreResolver.resolve(context, entry, index)
            if (mediaUri == null) {
                entry.status = ScanStatus.ERROR_NOT_IN_STORE
                entry.message = context.getString(R.string.msg_not_in_store_preflight)
                markedErrors++
                continue
            }

            if (entry.mediaStoreUri == null) {
                entry.mediaStoreUri = mediaUri
                resolvedNow++
            }

            if (skipAlreadyCorrect && MetadataRestorer.isDateAlreadyCorrect(context, entry, mediaUri)) {
                entry.status = ScanStatus.SKIPPED_ALREADY_CORRECT
                entry.message = context.getString(R.string.msg_date_already_correct)
                skippedCorrect++
            }
        }

        val stillReady = entries.count { it.status == ScanStatus.READY }
        return PreflightSummary(stillReady, skippedCorrect, markedErrors, resolvedNow)
    }
}
