package com.ohmy.zfsync.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoTransitionGeometryTest {

    private val screen = Size(1000f, 2000f)

    @Test
    fun `a landscape photo is center-cropped into the thumbnail square`() {
        val square = Rect(100f, 200f, 300f, 400f)
        val geometry = croppedGeometry(square, aspectRatio = 1.5f)
        assertEquals(square, geometry.clip)
        assertEquals(Rect(50f, 200f, 350f, 400f), geometry.image)
    }

    @Test
    fun `a portrait photo is center-cropped into the thumbnail square`() {
        val square = Rect(100f, 200f, 300f, 400f)
        val geometry = croppedGeometry(square, aspectRatio = 2f / 3f)
        assertEquals(Rect(100f, 150f, 300f, 450f), geometry.image)
    }

    @Test
    fun `the viewer fits a landscape photo to the screen width, centred`() {
        val geometry = viewerGeometry(screen, aspectRatio = 2f)
        val expected = Rect(0f, 750f, 1000f, 1250f)
        assertEquals(expected, geometry.image)
        assertEquals(expected, geometry.clip)
    }

    @Test
    fun `a zoomed photo is seen through the screen`() {
        val geometry = viewerGeometry(screen, aspectRatio = 0.5f, scale = 2f, offset = Offset(100f, 0f))
        // Fitted 1000x2000, zoomed to 2000x4000 about the centre, moved 100px right.
        assertEquals(Rect(-400f, -1000f, 1600f, 3000f), geometry.image)
        assertEquals(Rect(0f, 0f, 1000f, 2000f), geometry.clip)
    }

    @Test
    fun `halfway between list and viewer is halfway on both rects`() {
        val list = PhotoGeometry(Rect(0f, 0f, 100f, 100f), Rect(0f, 0f, 100f, 100f))
        val viewer = PhotoGeometry(Rect(0f, 0f, 300f, 500f), Rect(100f, 100f, 300f, 300f))
        val half = list.lerp(viewer, 0.5f)
        assertEquals(Rect(0f, 0f, 200f, 300f), half.image)
        assertEquals(Rect(50f, 50f, 200f, 200f), half.clip)
    }
}
