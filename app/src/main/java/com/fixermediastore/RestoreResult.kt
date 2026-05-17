package com.fixermediastore

sealed class RestoreResult {
    data class Success(val entry: ScanEntry) : RestoreResult()
    data class Failed(val entry: ScanEntry, val errorMessage: String) : RestoreResult()
    data class Skipped(val entry: ScanEntry, val reason: String) : RestoreResult()
}
