package com.ohmy.zfsync.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The home layout is stored by key (GalleryPreferencesStore keeps the string); this is how keys map back. */
class HomeLayoutTest {

    @Test
    fun `nothing saved means the list`() {
        assertEquals(HomeLayout.List, HomeLayout.fromKey(null))
    }

    @Test
    fun `each layout comes back from its saved key`() {
        for (layout in HomeLayout.entries) assertEquals(layout, HomeLayout.fromKey(layout.key))
    }

    @Test
    fun `keys are stable, since they're what's saved`() {
        assertEquals("list", HomeLayout.List.key)
        assertEquals("grid", HomeLayout.Grid.key)
    }

    @Test
    fun `an unknown key falls back to the list`() {
        assertEquals(HomeLayout.List, HomeLayout.fromKey("mosaic"))
    }

    @Test
    fun `the toggle switches between the two`() {
        assertEquals(HomeLayout.Grid, HomeLayout.List.toggled)
        assertEquals(HomeLayout.List, HomeLayout.Grid.toggled)
    }
}
