package com.gallery.app.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * One soft out-of-focus light, stored in normalized frame coordinates like the ink and patches so it
 * follows rotation, mirroring and cropping with the photo. [radius] is a fraction of the frame's
 * shorter side and [alpha] sets how strongly the orb glows.
 */
data class BokehOrb(
    val center: Offset,
    val radius: Float,
    val color: Color,
    val alpha: Float
)

/** The light sets the Bokeh row offers. None leaves the photo untouched. */
enum class BokehStyle { None, Warm, Golden, Cool, Dreamy, Night }

/**
 * The fixed set of lights a style adds. Hand-placed rather than random so the same photo always gets
 * the same effect and the preview and the export can never disagree. Larger orbs sit near the edges
 * where out-of-focus lights usually collect.
 */
val BokehStyle.orbs: List<BokehOrb>
    get() = when (this) {
        BokehStyle.None -> emptyList()
        BokehStyle.Warm -> WarmBokehOrbs
        BokehStyle.Golden -> GoldenBokehOrbs
        BokehStyle.Cool -> CoolBokehOrbs
        BokehStyle.Dreamy -> DreamyBokehOrbs
        BokehStyle.Night -> NightBokehOrbs
    }

/** Warm whites and golds with a few pink, teal and lavender highlights. */
private val WarmBokehOrbs: List<BokehOrb> = listOf(
    BokehOrb(Offset(0.12f, 0.16f), 0.10f, Color(0xFFFFF1D6), 0.30f),
    BokehOrb(Offset(0.30f, 0.08f), 0.05f, Color(0xFFFFC2DE), 0.28f),
    BokehOrb(Offset(0.52f, 0.15f), 0.07f, Color(0xFFFFD79A), 0.22f),
    BokehOrb(Offset(0.72f, 0.09f), 0.04f, Color(0xFFB8F2E9), 0.30f),
    BokehOrb(Offset(0.88f, 0.20f), 0.09f, Color(0xFFFFF1D6), 0.26f),
    BokehOrb(Offset(0.06f, 0.42f), 0.05f, Color(0xFFFFD79A), 0.22f),
    BokehOrb(Offset(0.94f, 0.46f), 0.06f, Color(0xFFFFC2DE), 0.24f),
    BokehOrb(Offset(0.20f, 0.62f), 0.08f, Color(0xFFB8F2E9), 0.20f),
    BokehOrb(Offset(0.44f, 0.55f), 0.035f, Color(0xFFD9CCFF), 0.20f),
    BokehOrb(Offset(0.66f, 0.68f), 0.10f, Color(0xFFFFF1D6), 0.24f),
    BokehOrb(Offset(0.85f, 0.78f), 0.05f, Color(0xFFFFD79A), 0.28f),
    BokehOrb(Offset(0.10f, 0.84f), 0.07f, Color(0xFFFFC2DE), 0.22f),
    BokehOrb(Offset(0.38f, 0.90f), 0.045f, Color(0xFFFFF1D6), 0.26f),
    BokehOrb(Offset(0.58f, 0.94f), 0.06f, Color(0xFFB8F2E9), 0.18f),
    BokehOrb(Offset(0.96f, 0.94f), 0.08f, Color(0xFFD9CCFF), 0.20f)
)

/** Sunset ambers: bigger, brighter orbs with a few pale creams. */
private val GoldenBokehOrbs: List<BokehOrb> = listOf(
    BokehOrb(Offset(0.15f, 0.20f), 0.13f, Color(0xFFFFD27A), 0.34f),
    BokehOrb(Offset(0.35f, 0.10f), 0.06f, Color(0xFFFFE6A8), 0.30f),
    BokehOrb(Offset(0.55f, 0.18f), 0.09f, Color(0xFFFFB347), 0.26f),
    BokehOrb(Offset(0.80f, 0.12f), 0.07f, Color(0xFFFFE6A8), 0.32f),
    BokehOrb(Offset(0.90f, 0.35f), 0.10f, Color(0xFFFFC46B), 0.24f),
    BokehOrb(Offset(0.08f, 0.50f), 0.08f, Color(0xFFFFB347), 0.22f),
    BokehOrb(Offset(0.28f, 0.62f), 0.05f, Color(0xFFFFF0C4), 0.30f),
    BokehOrb(Offset(0.60f, 0.55f), 0.11f, Color(0xFFFFD27A), 0.20f),
    BokehOrb(Offset(0.82f, 0.70f), 0.06f, Color(0xFFFFE6A8), 0.28f),
    BokehOrb(Offset(0.16f, 0.88f), 0.09f, Color(0xFFFFC46B), 0.24f),
    BokehOrb(Offset(0.45f, 0.92f), 0.05f, Color(0xFFFFF0C4), 0.26f),
    BokehOrb(Offset(0.72f, 0.94f), 0.08f, Color(0xFFFFD27A), 0.22f)
)

/** Blue hour: icy blues, cyans and a little lavender. */
private val CoolBokehOrbs: List<BokehOrb> = listOf(
    BokehOrb(Offset(0.10f, 0.14f), 0.10f, Color(0xFFBFE9FF), 0.28f),
    BokehOrb(Offset(0.32f, 0.09f), 0.05f, Color(0xFF8FD3FF), 0.26f),
    BokehOrb(Offset(0.55f, 0.16f), 0.08f, Color(0xFFC9C2FF), 0.22f),
    BokehOrb(Offset(0.78f, 0.10f), 0.06f, Color(0xFF7FE7E0), 0.30f),
    BokehOrb(Offset(0.92f, 0.28f), 0.09f, Color(0xFFBFE9FF), 0.24f),
    BokehOrb(Offset(0.06f, 0.38f), 0.06f, Color(0xFFC9C2FF), 0.22f),
    BokehOrb(Offset(0.22f, 0.55f), 0.09f, Color(0xFF8FD3FF), 0.20f),
    BokehOrb(Offset(0.48f, 0.48f), 0.04f, Color(0xFFE3F6FF), 0.26f),
    BokehOrb(Offset(0.70f, 0.62f), 0.10f, Color(0xFF7FE7E0), 0.22f),
    BokehOrb(Offset(0.90f, 0.72f), 0.05f, Color(0xFFBFE9FF), 0.28f),
    BokehOrb(Offset(0.12f, 0.80f), 0.08f, Color(0xFFC9C2FF), 0.20f),
    BokehOrb(Offset(0.38f, 0.88f), 0.06f, Color(0xFFE3F6FF), 0.24f),
    BokehOrb(Offset(0.62f, 0.92f), 0.09f, Color(0xFF8FD3FF), 0.22f),
    BokehOrb(Offset(0.88f, 0.94f), 0.07f, Color(0xFF7FE7E0), 0.26f)
)

/** Soft pastels: large pale pinks and lilacs for a dreamy portrait look. */
private val DreamyBokehOrbs: List<BokehOrb> = listOf(
    BokehOrb(Offset(0.14f, 0.12f), 0.12f, Color(0xFFFFD7EA), 0.32f),
    BokehOrb(Offset(0.38f, 0.18f), 0.07f, Color(0xFFE6D6FF), 0.28f),
    BokehOrb(Offset(0.62f, 0.10f), 0.09f, Color(0xFFFFE3F1), 0.26f),
    BokehOrb(Offset(0.86f, 0.22f), 0.06f, Color(0xFFD9CCFF), 0.30f),
    BokehOrb(Offset(0.08f, 0.44f), 0.08f, Color(0xFFFFD7EA), 0.24f),
    BokehOrb(Offset(0.30f, 0.48f), 0.11f, Color(0xFFE6D6FF), 0.20f),
    BokehOrb(Offset(0.55f, 0.38f), 0.05f, Color(0xFFFFF2F8), 0.28f),
    BokehOrb(Offset(0.78f, 0.52f), 0.09f, Color(0xFFFFD7EA), 0.24f),
    BokehOrb(Offset(0.94f, 0.62f), 0.07f, Color(0xFFD9CCFF), 0.26f),
    BokehOrb(Offset(0.20f, 0.70f), 0.10f, Color(0xFFFFE3F1), 0.22f),
    BokehOrb(Offset(0.46f, 0.78f), 0.06f, Color(0xFFE6D6FF), 0.26f),
    BokehOrb(Offset(0.72f, 0.86f), 0.08f, Color(0xFFFFD7EA), 0.22f),
    BokehOrb(Offset(0.92f, 0.92f), 0.05f, Color(0xFFFFF2F8), 0.30f)
)

/** Night lights: small, bright cyan and violet points with dark-blue halos. */
private val NightBokehOrbs: List<BokehOrb> = listOf(
    BokehOrb(Offset(0.08f, 0.18f), 0.06f, Color(0xFF4FC3F7), 0.30f),
    BokehOrb(Offset(0.26f, 0.10f), 0.035f, Color(0xFFB3E5FC), 0.34f),
    BokehOrb(Offset(0.46f, 0.20f), 0.05f, Color(0xFF7E57C2), 0.26f),
    BokehOrb(Offset(0.66f, 0.12f), 0.03f, Color(0xFF80DEEA), 0.32f),
    BokehOrb(Offset(0.84f, 0.24f), 0.055f, Color(0xFF4FC3F7), 0.28f),
    BokehOrb(Offset(0.94f, 0.40f), 0.04f, Color(0xFFB3E5FC), 0.30f),
    BokehOrb(Offset(0.14f, 0.46f), 0.045f, Color(0xFF7E57C2), 0.24f),
    BokehOrb(Offset(0.34f, 0.60f), 0.06f, Color(0xFF4FC3F7), 0.26f),
    BokehOrb(Offset(0.58f, 0.52f), 0.03f, Color(0xFFE1F5FE), 0.36f),
    BokehOrb(Offset(0.78f, 0.68f), 0.05f, Color(0xFF80DEEA), 0.28f),
    BokehOrb(Offset(0.10f, 0.82f), 0.04f, Color(0xFFB3E5FC), 0.30f),
    BokehOrb(Offset(0.42f, 0.90f), 0.055f, Color(0xFF4FC3F7), 0.26f),
    BokehOrb(Offset(0.68f, 0.94f), 0.035f, Color(0xFFE1F5FE), 0.34f),
    BokehOrb(Offset(0.90f, 0.88f), 0.06f, Color(0xFF7E57C2), 0.24f)
)
