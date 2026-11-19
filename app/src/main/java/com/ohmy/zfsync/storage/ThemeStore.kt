package com.ohmy.zfsync.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the color theme the user picked, as a cs-design palette name, or null for the
 * system colors. Stored by name so palettes added or reordered in later library versions don't
 * shift existing choices.
 */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _paletteName = MutableStateFlow(prefs.getString(KEY_PALETTE_NAME, null))
    val paletteName: StateFlow<String?> = _paletteName.asStateFlow()

    fun setPaletteName(name: String?) {
        prefs.edit().apply { if (name == null) remove(KEY_PALETTE_NAME) else putString(KEY_PALETTE_NAME, name) }.apply()
        _paletteName.value = name
    }

    private companion object {
        const val PREFS_NAME = "theme"
        const val KEY_PALETTE_NAME = "palette_name"
    }
}
