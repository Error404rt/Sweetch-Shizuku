package com.error404rt.sweetch

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Puzzle tile outline. Notches stay inside the measured bounds so pieces render cleanly
 * on small screens as well as in the accessibility-scaled layout.
 */
internal class PuzzlePieceShape(private val variant: Int) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height
        val notch = min(width, height) * 0.13f
        val radius = min(width, height) * 0.11f
        val path = Path()

        path.moveTo(radius, 0f)
        path.lineTo(width * 0.32f, 0f)
        if (variant % 2 == 0) {
            path.cubicTo(width * 0.40f, 0f, width * 0.40f, notch, width * 0.50f, notch)
            path.cubicTo(width * 0.60f, notch, width * 0.60f, 0f, width * 0.68f, 0f)
        } else {
            path.lineTo(width * 0.68f, 0f)
        }
        path.lineTo(width - radius, 0f)
        path.quadraticTo(width, 0f, width, radius)

        path.lineTo(width, height * 0.32f)
        if (variant % 3 == 0) {
            path.cubicTo(width, height * 0.40f, width - notch, height * 0.40f, width - notch, height * 0.50f)
            path.cubicTo(width - notch, height * 0.60f, width, height * 0.60f, width, height * 0.68f)
        } else {
            path.lineTo(width, height * 0.68f)
        }
        path.lineTo(width, height - radius)
        path.quadraticTo(width, height, width - radius, height)

        path.lineTo(width * 0.68f, height)
        if (variant % 2 != 0) {
            path.cubicTo(width * 0.60f, height, width * 0.60f, height - notch, width * 0.50f, height - notch)
            path.cubicTo(width * 0.40f, height - notch, width * 0.40f, height, width * 0.32f, height)
        } else {
            path.lineTo(width * 0.32f, height)
        }
        path.lineTo(radius, height)
        path.quadraticTo(0f, height, 0f, height - radius)

        path.lineTo(0f, height * 0.68f)
        if (variant % 3 == 1) {
            path.cubicTo(0f, height * 0.60f, notch, height * 0.60f, notch, height * 0.50f)
            path.cubicTo(notch, height * 0.40f, 0f, height * 0.40f, 0f, height * 0.32f)
        } else {
            path.lineTo(0f, height * 0.32f)
        }
        path.lineTo(0f, radius)
        path.quadraticTo(0f, 0f, radius, 0f)
        path.close()

        return Outline.Generic(path)
    }
}
