package com.fixermediastore

import android.content.Context

object ScanLogFormatter {

    private const val MAX_LOG_LINES = 400

    fun format(context: Context, entries: List<ScanEntry>, query: String = ""): String {
        val filtered = filter(entries, query)
        if (filtered.isEmpty()) return context.getString(R.string.log_dash)

        val errors = filtered.filter {
            it.status == ScanStatus.ERROR_NOT_IN_STORE || it.status == ScanStatus.ERROR_OTHER
        }
        val applied = filtered.filter { it.status == ScanStatus.APPLIED }
        val ready = filtered.filter { it.status == ScanStatus.READY }

        val showList = when {
            errors.isNotEmpty() -> errors + applied.take(50) + ready.take(30)
            else -> filtered
        }

        val hidden = filtered.size - showList.size
        val lines = showList.take(MAX_LOG_LINES).joinToString("\n") { entry ->
            formatLine(context, entry)
        }

        return buildString {
            if (errors.isNotEmpty()) {
                append(context.getString(R.string.log_errors_header, errors.size))
                append('\n')
            }
            append(lines)
            if (hidden > 0) {
                append('\n')
                append(context.getString(R.string.log_hidden_more, hidden))
            }
            if (filtered.size > MAX_LOG_LINES) {
                append('\n')
                append(context.getString(R.string.log_truncated, MAX_LOG_LINES, filtered.size))
            }
        }
    }

    private fun formatLine(context: Context, entry: ScanEntry): String {
        val date = entry.parsedDateTime?.toString() ?: context.getString(R.string.log_dash)
        val pattern = entry.matchedPattern ?: context.getString(R.string.log_dash)
        val source = sourceLabel(context, entry.dateSource)
        return "${entry.displayName} → $date → [$pattern|$source] → ${statusLabel(context, entry.status)} ${entry.message}"
    }

    private fun sourceLabel(context: Context, dateSource: String?): String = when (dateSource) {
        DateResolver.SOURCE_EXIF -> context.getString(R.string.log_source_exif)
        DateResolver.SOURCE_FILENAME -> context.getString(R.string.log_source_name)
        else -> context.getString(R.string.log_dash)
    }

    fun filter(entries: List<ScanEntry>, query: String): List<ScanEntry> {
        val q = query.trim()
        if (q.isEmpty()) return entries
        return entries.filter { it.displayName.contains(q, ignoreCase = true) }
    }

    fun countReady(entries: List<ScanEntry>): Int =
        entries.count { it.status == ScanStatus.READY }

    fun countPreflightable(entries: List<ScanEntry>): Int =
        entries.count {
            it.status == ScanStatus.READY ||
                (it.status == ScanStatus.ERROR_NOT_IN_STORE && it.parsedDateTime != null)
        }

    fun countSkipped(entries: List<ScanEntry>): Int =
        entries.count {
            it.status == ScanStatus.SKIPPED_NO_DATE ||
                it.status == ScanStatus.SKIPPED_ALREADY_CORRECT
        }

    fun countSkippedCorrect(entries: List<ScanEntry>): Int =
        entries.count { it.status == ScanStatus.SKIPPED_ALREADY_CORRECT }

    fun countSkippedNoDate(entries: List<ScanEntry>): Int =
        entries.count { it.status == ScanStatus.SKIPPED_NO_DATE }

    fun countFromExif(entries: List<ScanEntry>): Int =
        entries.count { it.dateSource == DateResolver.SOURCE_EXIF }

    fun countApplied(entries: List<ScanEntry>): Int =
        entries.count { it.status == ScanStatus.APPLIED }

    fun countErrors(entries: List<ScanEntry>): Int =
        entries.count {
            it.status == ScanStatus.ERROR_NOT_IN_STORE || it.status == ScanStatus.ERROR_OTHER
        }

    private fun statusLabel(context: Context, status: ScanStatus): String = when (status) {
        ScanStatus.PENDING -> context.getString(R.string.status_pending)
        ScanStatus.READY -> context.getString(R.string.status_ready)
        ScanStatus.SKIPPED_NO_DATE -> context.getString(R.string.status_skipped_no_date)
        ScanStatus.SKIPPED_ALREADY_CORRECT -> context.getString(R.string.status_skipped_correct)
        ScanStatus.APPLIED -> context.getString(R.string.status_applied)
        ScanStatus.ERROR_NOT_IN_STORE -> context.getString(R.string.status_not_in_store)
        ScanStatus.ERROR_OTHER -> context.getString(R.string.status_error)
    }
}
