package com.fixermediastore

import android.content.Context
import android.net.Uri

class FolderPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTreeUri(uri: Uri) {
        prefs.edit()
            .putString(KEY_TREE_URI, uri.toString())
            .apply()
    }

    fun loadTreeUri(): Uri? {
        val raw = prefs.getString(KEY_TREE_URI, null) ?: return null
        return Uri.parse(raw)
    }

    companion object {
        private const val PREFS_NAME = "fixer_prefs"
        private const val KEY_TREE_URI = "tree_uri"
    }
}
