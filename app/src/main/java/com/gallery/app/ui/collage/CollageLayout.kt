package com.gallery.app.ui.collage

/** One photo cell inside a collage template, in 0..1 coordinates of the page. */
data class CollageSlot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/** A collage template: the cells the selected photos are poured into, in order. */
data class CollageLayout(val slots: List<CollageSlot>)

/**
 * Templates offered for [count] photos. Two to four photos get hand-built arrangements; larger
 * selections get balanced grids, a wide top cell and a vertical strip of the same photos. Every
 * template always has exactly [count] cells.
 */
fun collageLayouts(count: Int): List<CollageLayout> {
    val n = count.coerceIn(2, 9)
    val candidates = when (n) {
        2 -> listOf(
            CollageLayout(rows(2)),
            CollageLayout(columns(2)),
            CollageLayout(featureTop(2)),
            CollageLayout(featureLeft(2))
        )

        3 -> listOf(
            CollageLayout(rows(3)),
            CollageLayout(columns(3)),
            CollageLayout(featureTop(3)),
            CollageLayout(featureLeft(3))
        )

        4 -> listOf(
            CollageLayout(grid(4, 2)),
            CollageLayout(rows(4)),
            CollageLayout(columns(4)),
            CollageLayout(featureLeft(4))
        )

        else -> listOf(
            CollageLayout(grid(n, 3)),
            CollageLayout(grid(n, 4)),
            CollageLayout(featureTop(n)),
            CollageLayout(columns(n))
        )
    }
    return candidates.distinctBy { it.slots }
}

private fun rows(count: Int): List<CollageSlot> =
    List(count) { index ->
        CollageSlot(
            left = 0f,
            top = index.toFloat() / count,
            right = 1f,
            bottom = (index + 1f) / count
        )
    }

private fun columns(count: Int): List<CollageSlot> =
    List(count) { index ->
        CollageSlot(
            left = index.toFloat() / count,
            top = 0f,
            right = (index + 1f) / count,
            bottom = 1f
        )
    }

/**
 * [count] cells in [columns] columns. A short last row stretches its cells across the full width
 * instead of leaving a hole, so the page is always completely covered.
 */
private fun grid(count: Int, columns: Int): List<CollageSlot> {
    val rowCount = (count + columns - 1) / columns
    val slots = mutableListOf<CollageSlot>()
    repeat(rowCount) { row ->
        val inRow = minOf(columns, count - row * columns)
        repeat(inRow) { column ->
            slots += CollageSlot(
                left = column.toFloat() / inRow,
                top = row.toFloat() / rowCount,
                right = (column + 1f) / inRow,
                bottom = (row + 1f) / rowCount
            )
        }
    }
    return slots
}

/** One wide lead photo across the top, the rest in a grid underneath. */
private fun featureTop(count: Int): List<CollageSlot> {
    val lead = 0.42f
    val rest = count - 1
    val restColumns = when {
        rest <= 3 -> rest
        else -> 3
    }
    val restRows = (rest + restColumns - 1) / restColumns
    val slots = mutableListOf(CollageSlot(0f, 0f, 1f, lead))
    repeat(restRows) { row ->
        val inRow = minOf(restColumns, rest - row * restColumns)
        repeat(inRow) { column ->
            slots += CollageSlot(
                left = column.toFloat() / inRow,
                top = lead + (1f - lead) * row / restRows,
                right = (column + 1f) / inRow,
                bottom = lead + (1f - lead) * (row + 1f) / restRows
            )
        }
    }
    return slots
}

/** One tall lead photo on the left, the rest stacked on the right. */
private fun featureLeft(count: Int): List<CollageSlot> {
    val lead = 0.62f
    val rest = count - 1
    val slots = mutableListOf(CollageSlot(0f, 0f, lead, 1f))
    repeat(rest) { index ->
        slots += CollageSlot(
            left = lead,
            top = index.toFloat() / rest,
            right = 1f,
            bottom = (index + 1f) / rest
        )
    }
    return slots
}
