package com.ohmy.zfsync.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers gallery display preferences across launches. */
class GalleryPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _filmStripCarousel = MutableStateFlow(prefs.getBoolean(KEY_FILM_STRIP_CAROUSEL, false))

    /** Whether the gallery's carousel uses the v2 film strip look; off by default. */
    val filmStripCarousel: StateFlow<Boolean> = _filmStripCarousel.asStateFlow()

    fun setFilmStripCarousel(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILM_STRIP_CAROUSEL, enabled).apply()
        _filmStripCarousel.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "gallery"
        const val KEY_FILM_STRIP_CAROUSEL = "film_strip_carousel"
    }
}
