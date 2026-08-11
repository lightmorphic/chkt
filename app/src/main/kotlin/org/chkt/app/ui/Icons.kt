package org.chkt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The CHKT mark: brand-yellow rounded square with a navy tick. */
@Composable
fun ChktLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(28.dp)) {
        val s = size.minDimension / 24f
        drawRoundRect(
            color = Color(0xFFFBC711),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f * s, 5f * s),
        )
        val tick = Path().apply {
            moveTo(6.5f * s, 12.5f * s); lineTo(10.5f * s, 16.5f * s); lineTo(17.5f * s, 7.5f * s)
        }
        drawPath(tick, Color(0xFF111827),
            style = Stroke(width = 2.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Purpose-drawn stroke icons (no emoji, no icon-font glyphs). Each is drawn
 * on a 24×24 grid and scales with the size modifier.
 */
enum class ChktIcon { Edit, Delete, Tick, Add, Back, Settings, Stats, Bell, Speaker, Clock, Pin }

@Composable
fun IconButton24(
    icon: ChktIcon,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Canvas(
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics { contentDescription = label },
    ) {
        val s = size.minDimension / 24f
        fun p(build: Path.() -> Unit) = Path().apply(build)
        val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val paths: List<Path> = when (icon) {
            ChktIcon.Edit -> listOf(p {
                moveTo(4f * s, 20f * s); lineTo(8f * s, 19f * s); lineTo(19f * s, 8f * s)
                lineTo(16f * s, 5f * s); lineTo(5f * s, 16f * s); close()
            })
            ChktIcon.Delete -> listOf(
                p { moveTo(5f * s, 7f * s); lineTo(19f * s, 7f * s) },
                p { moveTo(9f * s, 7f * s); lineTo(9f * s, 4f * s); lineTo(15f * s, 4f * s); lineTo(15f * s, 7f * s) },
                p { moveTo(7f * s, 7f * s); lineTo(8f * s, 20f * s); lineTo(16f * s, 20f * s); lineTo(17f * s, 7f * s) },
            )
            ChktIcon.Tick -> listOf(p { moveTo(5f * s, 13f * s); lineTo(10f * s, 18f * s); lineTo(19f * s, 6f * s) })
            ChktIcon.Add -> listOf(
                p { moveTo(12f * s, 5f * s); lineTo(12f * s, 19f * s) },
                p { moveTo(5f * s, 12f * s); lineTo(19f * s, 12f * s) },
            )
            ChktIcon.Back -> listOf(p { moveTo(15f * s, 5f * s); lineTo(8f * s, 12f * s); lineTo(15f * s, 19f * s) })
            ChktIcon.Settings -> listOf(
                p { moveTo(4f * s, 7f * s); lineTo(20f * s, 7f * s) },
                p { moveTo(4f * s, 12f * s); lineTo(20f * s, 12f * s) },
                p { moveTo(4f * s, 17f * s); lineTo(20f * s, 17f * s) },
                p { addOval(androidx.compose.ui.geometry.Rect(13f * s, 5f * s, 17f * s, 9f * s)) },
                p { addOval(androidx.compose.ui.geometry.Rect(7f * s, 10f * s, 11f * s, 14f * s)) },
                p { addOval(androidx.compose.ui.geometry.Rect(12f * s, 15f * s, 16f * s, 19f * s)) },
            )
            ChktIcon.Stats -> listOf(
                p { moveTo(5f * s, 20f * s); lineTo(5f * s, 13f * s) },
                p { moveTo(10f * s, 20f * s); lineTo(10f * s, 8f * s) },
                p { moveTo(15f * s, 20f * s); lineTo(15f * s, 11f * s) },
                p { moveTo(20f * s, 20f * s); lineTo(20f * s, 5f * s) },
            )
            ChktIcon.Bell -> listOf(
                p {
                    moveTo(6f * s, 17f * s); lineTo(18f * s, 17f * s); lineTo(16.5f * s, 14f * s)
                    lineTo(16.5f * s, 10f * s)
                    arcTo(androidx.compose.ui.geometry.Rect(7.5f * s, 5.5f * s, 16.5f * s, 14.5f * s), 0f, -180f, false)
                    lineTo(7.5f * s, 14f * s); close()
                },
                p { moveTo(10.5f * s, 19.5f * s); lineTo(13.5f * s, 19.5f * s) },
            )
            ChktIcon.Speaker -> listOf(
                p {
                    moveTo(5f * s, 10f * s); lineTo(8f * s, 10f * s); lineTo(13f * s, 6f * s)
                    lineTo(13f * s, 18f * s); lineTo(8f * s, 14f * s); lineTo(5f * s, 14f * s); close()
                },
                p { moveTo(16f * s, 9f * s); quadraticBezierTo(19f * s, 12f * s, 16f * s, 15f * s) },
            )
            ChktIcon.Clock -> listOf(
                p { addOval(androidx.compose.ui.geometry.Rect(4f * s, 4f * s, 20f * s, 20f * s)) },
                p { moveTo(12f * s, 8f * s); lineTo(12f * s, 12f * s); lineTo(15f * s, 14f * s) },
            )
            ChktIcon.Pin -> listOf(
                p {
                    moveTo(12f * s, 21f * s)
                    quadraticBezierTo(5f * s, 13f * s, 5f * s, 9.5f * s)
                    arcTo(androidx.compose.ui.geometry.Rect(5f * s, 3f * s, 19f * s, 16f * s), 180f, -180f, false)
                    quadraticBezierTo(19f * s, 13f * s, 12f * s, 21f * s); close()
                },
                p { addOval(androidx.compose.ui.geometry.Rect(10f * s, 7.5f * s, 14f * s, 11.5f * s)) },
            )
        }
        paths.forEach { drawPath(it, tint, style = stroke) }
    }
}
