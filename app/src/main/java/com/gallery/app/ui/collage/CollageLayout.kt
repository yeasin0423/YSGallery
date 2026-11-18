package com.gallery.app.ui.collage

/** How a photo cell is cut out of its frame. */
enum class CollageShape { Rounded, Square, Circle, Heart, Hexagon, Diamond }

/** The typeface families used by the printed captions. */
enum class CollageTextStyle { Serif, SerifBold, Typewriter, Sans, SansLight, Cursive }

/** Horizontal alignment of a caption inside its box. */
enum class CollageAlign { Start, Center, End }

/** One photo cell inside a collage template, in 0..1 coordinates of the page. */
data class CollageSlot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Overrides the template's shape for this cell (the six-photo card mixes shapes). */
    val shape: CollageShape? = null
)

/** A caption printed on the page. [size] is a fraction of the page width. */
data class CollageText(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val size: Float,
    val color: Int,
    val style: CollageTextStyle = CollageTextStyle.Sans,
    val align: CollageAlign = CollageAlign.Center,
    val letterSpacing: Float = 0f,
    val rotation: Float = 0f
)

/** A small emoji decoration at a normalized point; [size] is a fraction of the page width. */
data class CollageDecor(
    val emoji: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float = 0f
)

/** A filled band behind the photos, used for caption strips and cards. */
data class CollageBand(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: Int
)

/**
 * A collage template: the cells the selected photos are poured into, in order, plus the page style.
 * The first template for each count is the decorated one from the gallery's own sheets; the rest are
 * plain alternates.
 */
data class CollageLayout(
    val slots: List<CollageSlot>,
    val background: Int = 0xFFFFFFFF.toInt(),
    val backgroundBottom: Int = background,
    val shape: CollageShape = CollageShape.Rounded,
    val margin: Float = 0.035f,
    val gap: Float = 0.014f,
    val corner: Float = 0.012f,
    /** When set, every photo gets a border of this colour (the white mats on the printed sheets). */
    val frame: Int? = null,
    val frameWidth: Float = 0.014f,
    val bands: List<CollageBand> = emptyList(),
    val texts: List<CollageText> = emptyList(),
    val decors: List<CollageDecor> = emptyList()
)

private const val White = 0xFFFFFFFF.toInt()
private const val Cream = 0xFFF7EADB.toInt()
private const val Peach = 0xFFFBE2C4.toInt()
private const val Lavender = 0xFFB9A5DB.toInt()
private const val Ink = 0xFF26211D.toInt()
private const val Brown = 0xFF7C4A2A.toInt()
private const val Grey = 0xFF6E6E6E.toInt()
private const val Orange = 0xFFE08A3C.toInt()

/**
 * Templates offered for [count] photos. The first one is decorated like the reference sheets (2–9
 * photos); three plain alternates follow. Every template always has exactly [count] cells.
 */
fun collageLayouts(count: Int): List<CollageLayout> {
    val n = count.coerceIn(2, 9)
    val candidates = listOf(decoratedLayout(n)) + plainLayouts(n)
    return candidates.distinctBy { it.slots }
}

private fun plainLayouts(n: Int): List<CollageLayout> = when (n) {
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

/** The decorated sheet for [count], matching the gallery's own quote-and-sticker templates. */
private fun decoratedLayout(count: Int): CollageLayout = when (count) {
    2 -> CollageLayout(
        slots = listOf(
            CollageSlot(0.09f, 0.06f, 0.91f, 0.36f),
            CollageSlot(0.13f, 0.44f, 0.87f, 0.72f)
        ),
        background = Cream,
        shape = CollageShape.Square,
        frame = White,
        frameWidth = 0.02f,
        texts = listOf(
            CollageText(
                text = "happy story of my life",
                left = 0.10f, top = 0.785f, right = 0.90f, bottom = 0.845f,
                size = 0.060f, color = Ink, style = CollageTextStyle.SerifBold
            )
        ),
        decors = listOf(
            CollageDecor("🍀", 0.30f, 0.405f, 0.040f),
            CollageDecor("🍀", 0.83f, 0.395f, 0.040f),
            CollageDecor("🍀", 0.50f, 0.755f, 0.040f)
        )
    )

    3 -> CollageLayout(
        slots = listOf(
            CollageSlot(0.06f, 0.05f, 0.46f, 0.30f, CollageShape.Circle),
            CollageSlot(0.21f, 0.24f, 0.79f, 0.65f, CollageShape.Circle),
            CollageSlot(0.55f, 0.55f, 0.95f, 0.80f, CollageShape.Circle)
        ),
        background = White,
        shape = CollageShape.Circle,
        texts = listOf(
            CollageText(
                text = "fashion",
                left = 0.55f, top = 0.080f, right = 0.95f, bottom = 0.125f,
                size = 0.048f, color = Ink, style = CollageTextStyle.Typewriter,
                letterSpacing = 0.02f
            ),
            CollageText(
                text = "Sunny Mood",
                left = 0.10f, top = 0.665f, right = 0.48f, bottom = 0.705f,
                size = 0.026f, color = Grey
            ),
            CollageText(
                text = "Keep head held high",
                left = 0.05f, top = 0.715f, right = 0.53f, bottom = 0.755f,
                size = 0.026f, color = Grey
            )
        )
    )

    4 -> CollageLayout(
        slots = listOf(
            CollageSlot(0.09f, 0.13f, 0.46f, 0.36f),
            CollageSlot(0.54f, 0.13f, 0.91f, 0.36f),
            CollageSlot(0.09f, 0.38f, 0.46f, 0.61f),
            CollageSlot(0.54f, 0.38f, 0.91f, 0.61f)
        ),
        background = Lavender,
        shape = CollageShape.Square,
        frame = White,
        frameWidth = 0.016f,
        texts = listOf(
            CollageText(
                text = "have a good day",
                left = 0.10f, top = 0.645f, right = 0.80f, bottom = 0.695f,
                size = 0.038f, color = White, style = CollageTextStyle.SansLight,
                align = CollageAlign.Start, letterSpacing = 0.06f
            )
        ),
        decors = listOf(
            CollageDecor("🎈", 0.37f, 0.065f, 0.075f),
            CollageDecor("🌈", 0.12f, 0.620f, 0.055f)
        )
    )

    5 -> CollageLayout(
        slots = listOf(
            CollageSlot(0.06f, 0.05f, 0.46f, 0.29f, CollageShape.Circle),
            CollageSlot(0.54f, 0.05f, 0.94f, 0.29f, CollageShape.Circle),
            CollageSlot(0.06f, 0.31f, 0.46f, 0.55f, CollageShape.Circle),
            CollageSlot(0.54f, 0.31f, 0.94f, 0.55f, CollageShape.Circle),
            CollageSlot(0.06f, 0.57f, 0.46f, 0.81f, CollageShape.Circle)
        ),
        background = White,
        shape = CollageShape.Circle,
        texts = listOf(
            CollageText(
                text = "Memory",
                left = 0.52f, top = 0.655f, right = 0.96f, bottom = 0.710f,
                size = 0.052f, color = Ink, style = CollageTextStyle.Cursive,
                rotation = -4f
            ),
            CollageText(
                text = "You are my sunshine",
                left = 0.50f, top = 0.720f, right = 0.98f, bottom = 0.765f,
                size = 0.028f, color = Ink, style = CollageTextStyle.Cursive,
                rotation = -6f
            )
        ),
        decors = listOf(CollageDecor("☀️", 0.73f, 0.620f, 0.070f))
    )

    6 -> CollageLayout(
        slots = listOf(
            CollageSlot(0.09f, 0.03f, 0.91f, 0.33f),
            CollageSlot(0.13f, 0.47f, 0.33f, 0.62f, CollageShape.Hexagon),
            CollageSlot(0.40f, 0.47f, 0.60f, 0.62f, CollageShape.Heart),
            CollageSlot(0.67f, 0.47f, 0.87f, 0.62f, CollageShape.Circle),
            CollageSlot(0.24f, 0.63f, 0.44f, 0.78f, CollageShape.Circle),
            CollageSlot(0.56f, 0.63f, 0.76f, 0.78f, CollageShape.Hexagon)
        ),
        background = Cream,
        shape = CollageShape.Square,
        bands = listOf(CollageBand(0.09f, 0.345f, 0.91f, 0.965f, White)),
        texts = listOf(
            CollageText(
                text = "SWEETY & MEMORY",
                left = 0.14f, top = 0.405f, right = 0.86f, bottom = 0.445f,
                size = 0.036f, color = Brown, style = CollageTextStyle.SansLight,
                letterSpacing = 0.05f
            )
        ),
        decors = listOf(
            CollageDecor("🤍", 0.50f, 0.375f, 0.050f),
            CollageDecor("✨", 0.44f, 0.372f, 0.022f),
            CollageDecor("✨", 0.56f, 0.372f, 0.022f)
        )
    )

    7 -> CollageLayout(
        slots = listOf(
            diamondSlot(0.35f, 0.20f),
            diamondSlot(0.65f, 0.20f),
            diamondSlot(0.20f, 0.30f),
            diamondSlot(0.50f, 0.30f),
            diamondSlot(0.80f, 0.30f),
            diamondSlot(0.35f, 0.40f),
            diamondSlot(0.65f, 0.40f)
        ),
        background = White,
        shape = CollageShape.Diamond,
        texts = listOf(
            CollageText(
                text = "That man is the richest\nwhose pleasure are the cheapest.",
                left = 0.10f, top = 0.585f, right = 0.90f, bottom = 0.665f,
                size = 0.029f, color = Ink, style = CollageTextStyle.Typewriter,
                letterSpacing = 0.01f
            )
        ),
        decors = listOf(
            CollageDecor("🌤️", 0.13f, 0.075f, 0.055f),
            CollageDecor("🦒", 0.79f, 0.730f, 0.085f)
        )
    )

    8 -> CollageLayout(
        slots = listOf(
            CollageSlot(0.09f, 0.03f, 0.363f, 0.175f),
            CollageSlot(0.364f, 0.03f, 0.637f, 0.175f),
            CollageSlot(0.638f, 0.03f, 0.91f, 0.175f),
            CollageSlot(0.09f, 0.176f, 0.363f, 0.32f),
            CollageSlot(0.364f, 0.176f, 0.637f, 0.32f),
            CollageSlot(0.638f, 0.176f, 0.91f, 0.32f),
            CollageSlot(0.09f, 0.55f, 0.54f, 0.79f),
            CollageSlot(0.55f, 0.55f, 0.91f, 0.79f)
        ),
        background = White,
        shape = CollageShape.Square,
        gap = 0.004f,
        bands = listOf(CollageBand(0.09f, 0.325f, 0.91f, 0.365f, Cream)),
        texts = listOf(
            CollageText(
                text = "Travel/ time",
                left = 0.10f, top = 0.388f, right = 0.90f, bottom = 0.425f,
                size = 0.030f, color = Ink, style = CollageTextStyle.Typewriter
            ),
            CollageText(
                text = "Nice. TRIP",
                left = 0.10f, top = 0.430f, right = 0.90f, bottom = 0.495f,
                size = 0.072f, color = Ink, style = CollageTextStyle.SerifBold
            ),
            CollageText(
                text = "I am here",
                left = 0.12f, top = 0.715f, right = 0.42f, bottom = 0.762f,
                size = 0.032f, color = Orange, style = CollageTextStyle.SansLight,
                align = CollageAlign.Start
            )
        ),
        decors = listOf(
            CollageDecor("📋", 0.18f, 0.345f, 0.026f),
            CollageDecor("📍", 0.50f, 0.345f, 0.026f),
            CollageDecor("☀️", 0.82f, 0.345f, 0.026f)
        )
    )

    else -> CollageLayout(
        slots = listOf(
            CollageSlot(0.13f, 0.17f, 0.87f, 0.40f),
            CollageSlot(0.09f, 0.45f, 0.292f, 0.545f),
            CollageSlot(0.296f, 0.45f, 0.498f, 0.545f),
            CollageSlot(0.502f, 0.45f, 0.704f, 0.545f),
            CollageSlot(0.708f, 0.45f, 0.91f, 0.545f),
            CollageSlot(0.09f, 0.55f, 0.292f, 0.645f),
            CollageSlot(0.296f, 0.55f, 0.498f, 0.645f),
            CollageSlot(0.502f, 0.55f, 0.704f, 0.645f),
            CollageSlot(0.708f, 0.55f, 0.91f, 0.645f)
        ),
        background = Peach,
        shape = CollageShape.Square,
        gap = 0.004f,
        texts = listOf(
            CollageText(
                text = "Share",
                left = 0.31f, top = 0.048f, right = 0.46f, bottom = 0.080f,
                size = 0.026f, color = Brown, style = CollageTextStyle.Serif,
                align = CollageAlign.Start
            ),
            CollageText(
                text = "NICE",
                left = 0.09f, top = 0.078f, right = 0.43f, bottom = 0.132f,
                size = 0.060f, color = Brown, style = CollageTextStyle.Cursive
            ),
            CollageText(
                text = "TIME",
                left = 0.57f, top = 0.078f, right = 0.91f, bottom = 0.132f,
                size = 0.060f, color = Brown, style = CollageTextStyle.Cursive
            )
        ),
        decors = listOf(
            CollageDecor("☕", 0.50f, 0.095f, 0.052f),
            CollageDecor("🐕", 0.50f, 0.140f, 0.030f)
        )
    )
}

/** A square diamond whose half-height is 0.11 of the page, centred at [x]/[y]. */
private fun diamondSlot(x: Float, y: Float): CollageSlot {
    val halfHeight = 0.11f
    // The page is 3:4, so a square in pixels is wider than it is tall in normalized coordinates.
    val halfWidth = halfHeight * 4f / 3f
    return CollageSlot(
        left = x - halfWidth,
        top = y - halfHeight,
        right = x + halfWidth,
        bottom = y + halfHeight,
        shape = CollageShape.Diamond
    )
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
