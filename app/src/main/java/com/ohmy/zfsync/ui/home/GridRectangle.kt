package com.ohmy.zfsync.ui.home

/** Where an item sits in a grid: its line (counted down the whole grid) and its column. */
data class GridCell(val row: Int, val column: Int)

/**
 * Each item's cell in a lazy grid of [columns] columns, laid out as LazyVerticalGrid does:
 * items fill each line left to right, and a full-width item ([fullSpan]: a header, say) takes a
 * line of its own, ending any part-filled line before it. Full-width items have no cell (null).
 */
fun <T> gridCells(items: List<T>, columns: Int, fullSpan: (T) -> Boolean): List<GridCell?> {
    val cells = ArrayList<GridCell?>(items.size)
    var row = 0
    var column = 0
    for (item in items) {
        if (fullSpan(item)) {
            if (column > 0) row++
            cells += null
            row++
            column = 0
        } else {
            cells += GridCell(row, column)
            column++
            if (column == columns) {
                row++
                column = 0
            }
        }
    }
    return cells
}

/**
 * The keys of [cells] inside the rectangle with [from] and [to] at opposite corners: every
 * line from the first one's to the second's, and in each, every column between theirs.
 */
fun <K> keysInRectangle(cells: Map<K, GridCell>, from: GridCell, to: GridCell): List<K> {
    val rows = minOf(from.row, to.row)..maxOf(from.row, to.row)
    val columns = minOf(from.column, to.column)..maxOf(from.column, to.column)
    return cells.filter { (_, cell) -> cell.row in rows && cell.column in columns }.keys.toList()
}
