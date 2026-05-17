package com.fixermediastore

import android.content.Context

class AppPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var skipErrorsContinue: Boolean
        get() = prefs.getBoolean(KEY_SKIP_ERRORS, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_ERRORS, value).apply()

    var skipAlreadyCorrect: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CORRECT, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_CORRECT, value).apply()

    var useExifWhenNoFilename: Boolean
        get() = prefs.getBoolean(KEY_USE_EXIF, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_EXIF, value).apply()

    var filtersPanelExpanded: Boolean
        get() = prefs.getBoolean(KEY_FILTERS_EXPANDED, false)
        set(value) = prefs.edit().putBoolean(KEY_FILTERS_EXPANDED, value).apply()

    var logPanelExpanded: Boolean
        get() = prefs.getBoolean(KEY_LOG_EXPANDED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOG_EXPANDED, value).apply()

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_SKIP_ERRORS = "skip_errors"
        private const val KEY_SKIP_CORRECT = "skip_correct"
        private const val KEY_USE_EXIF = "use_exif"
        private const val KEY_FILTERS_EXPANDED = "filters_expanded"
        private const val KEY_LOG_EXPANDED = "log_expanded"
    }
}
