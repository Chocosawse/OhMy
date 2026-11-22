package com.ohmy.zfsync.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class GridRectangleTest {

    /** "H" is a full-width heading; anything else is a photo. */
    private fun cellsOf(vararg items: String, columns: Int = 3): Map<String, GridCell> =
        gridCells(items.toList(), columns) { it.startsWith("H") }
            .zip(items)
            .mapNotNull { (cell, item) -> cell?.let { item to it } }
            .toMap()

    @Test
    fun `photos fill lines left to right, and a heading takes a line of its own`() {
        val cells = cellsOf("H1", "a", "b", "c", "d", "H2", "e")
        assertEquals(GridCell(1, 0), cells["a"])
        assertEquals(GridCell(1, 2), cells["c"])
        // A full line wraps to the next.
        assertEquals(GridCell(2, 0), cells["d"])
        // The heading ends d's part-filled line, taking line 3; e starts line 4.
        assertEquals(GridCell(4, 0), cells["e"])
    }

    @Test
    fun `the rectangle runs between the two corners whichever way the drag went`() {
        // Lines: 0 a b c / 1 d e f / 2 g h i
        val cells = cellsOf("a", "b", "c", "d", "e", "f", "g", "h", "i")
        val down = keysInRectangle(cells, cells.getValue("b"), cells.getValue("f"))
        assertEquals(setOf("b", "c", "e", "f"), down.toSet())
        // Dragged up and to the left from i to e: the same kind of rectangle.
        val up = keysInRectangle(cells, cells.getValue("i"), cells.getValue("e"))
        assertEquals(setOf("e", "f", "h", "i"), up.toSet())
        // Straight down a column.
        assertEquals(setOf("a", "d", "g"), keysInRectangle(cells, cells.getValue("a"), cells.getValue("g")).toSet())
    }

    @Test
    fun `a rectangle spans headings and short lines`() {
        // Lines: 0 a b / 1 heading / 2 c d e
        val cells = cellsOf("a", "b", "H", "c", "d", "e")
        val selected = keysInRectangle(cells, cells.getValue("b"), cells.getValue("e")).toSet()
        assertEquals(setOf("b", "d", "e"), selected)
    }
}
