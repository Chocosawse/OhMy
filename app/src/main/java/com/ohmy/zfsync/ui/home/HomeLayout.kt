package com.ohmy.zfsync.ui.home

/** How the home screen lays out the album: rows with details, or a grid of photos alone. */
enum class HomeLayout(val key: String) {
    List("list"),
    Grid("grid"),
    ;

    /** The other layout: what the library row's toggle switches to. */
    val toggled: HomeLayout get() = if (this == List) Grid else List

    companion object {
        /** Unknown or missing keys fall back to [List]. */
        fun fromKey(key: String?): HomeLayout = entries.firstOrNull { it.key == key } ?: List
    }
}
