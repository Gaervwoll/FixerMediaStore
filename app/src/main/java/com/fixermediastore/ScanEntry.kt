package com.fixermediastore

import android.net.Uri
import java.time.LocalDateTime

data class ScanEntry(
    val displayName: String,
    val documentUri: Uri,
    val parsedDateTime: LocalDateTime?,
    var mediaStoreUri: Uri? = null,
    /** Путь папки из SAF, например DCIM/Camera/ — для поиска в MediaStore */
    var relativePathHint: String? = null,
    val matchedPattern: String? = null,
    /** Источник даты: filename или exif */
    val dateSource: String? = null,
    var status: ScanStatus = ScanStatus.PENDING,
    var message: String = ""
)

enum class ScanStatus {
    PENDING,
    READY,
    SKIPPED_NO_DATE,
    /** Дата в MediaStore уже совпадает с датой из имени — не трогаем */
    SKIPPED_ALREADY_CORRECT,
    APPLIED,
    ERROR_NOT_IN_STORE,
    ERROR_OTHER
}
