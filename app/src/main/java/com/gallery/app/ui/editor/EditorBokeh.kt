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

/**
 * The fixed set of lights the Bokeh switch adds. Hand-placed rather than random so the same photo
 * always gets the same effect and the preview and the export can never disagree. Warm whites and
 * golds dominate, with a few pink, teal and lavender highlights, and the larger orbs sit near the
 * edges where out-of-focus lights usually collect.
 */
val BokehOrbs: List<BokehOrb> = listOf(
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
