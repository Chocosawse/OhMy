package com.ohmy.zfsync.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers gallery display preferences across launches. */
class GalleryPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _carouselStyle = MutableStateFlow(loadCarouselStyle())

    /**
     * The gallery carousel's style, as its saved key (see CarouselStyle), or null for the default.
     * Stored as a string so the UI's styles can change without touching storage.
     */
    val carouselStyle: StateFlow<String?> = _carouselStyle.asStateFlow()

    fun setCarouselStyle(key: String) {
        prefs.edit().putString(KEY_CAROUSEL_STYLE, key).remove(KEY_LEGACY_FILM_STRIP_CAROUSEL).apply()
        _carouselStyle.value = key
    }

    private val _confirmDelete = MutableStateFlow(prefs.getBoolean(KEY_CONFIRM_DELETE, true))

    /** Whether deleting a photo from the viewer asks first; on by default ("Don't ask again" turns it off). */
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()

    fun setConfirmDelete(confirm: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_DELETE, confirm).apply()
        _confirmDelete.value = confirm
    }

    /** Before there was a choice of styles there was an on/off film strip switch: on means the v2 film strip. */
    private fun loadCarouselStyle(): String? =
        prefs.getString(KEY_CAROUSEL_STYLE, null)
            ?: LEGACY_FILM_STRIP_STYLE_KEY.takeIf { prefs.getBoolean(KEY_LEGACY_FILM_STRIP_CAROUSEL, false) }

    private companion object {
        const val PREFS_NAME = "gallery"
        const val KEY_CAROUSEL_STYLE = "carousel_style"
        const val KEY_CONFIRM_DELETE = "confirm_delete"
        const val KEY_LEGACY_FILM_STRIP_CAROUSEL = "film_strip_carousel"
        /** CarouselStyle.FilmStrip's key. */
        const val LEGACY_FILM_STRIP_STYLE_KEY = "film_strip"
    }
}
