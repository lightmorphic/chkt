package org.chkt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.chkt.app.update.Updater
import java.io.File

/**
 * Lightmorphic's standard update-status dot: green when current, amber with
 * a download glyph when an update is out, a filling ring while it downloads,
 * green with an install glyph once ready, red if GitHub can't be reached.
 * One check on open; no separate "check for updates" control.
 */
private sealed class DotState {
    data object Checking : DotState()
    data object UpToDate : DotState()
    data class Available(val info: Updater.UpdateInfo) : DotState()
    data object Downloading : DotState()
    data class Ready(val apk: File) : DotState()
    data object Unreachable : DotState()
}

private val GREEN = Color(0xFF4BAE4F)
private val AMBER = Color(0xFFFFC006)
private val RED = Color(0xFFF34236)

@Composable
fun UpdateStatusDot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DotState>(DotState.Checking) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        state = when (val result = Updater.check()) {
            is Updater.CheckResult.UpToDate -> DotState.UpToDate
            is Updater.CheckResult.UpdateAvailable -> DotState.Available(result.info)
            is Updater.CheckResult.Failed -> DotState.Unreachable
        }
    }

    val current = state
    val description: String
    val onClick: (() -> Unit)?
    when (current) {
        is DotState.Checking -> { description = "Checking for updates"; onClick = null }
        is DotState.UpToDate -> { description = "Up to date"; onClick = null }
        is DotState.Available -> {
            description = "Update available, tap to download"
            onClick = {
                val info = current.info
                scope.launch {
                    state = DotState.Downloading
                    progress = 0f
                    when (val result = Updater.downloadUpdate(context, info) { p -> progress = p }) {
                        is Updater.DownloadResult.Ok -> state = DotState.Ready(result.apk)
                        is Updater.DownloadResult.Failed -> state = DotState.Unreachable
                    }
                }
            }
        }
        is DotState.Downloading -> { description = "Downloading update"; onClick = null }
        is DotState.Ready -> {
            description = "Downloaded, tap to install"
            onClick = { Updater.install(context, current.apk) }
        }
        is DotState.Unreachable -> { description = "Can't reach GitHub"; onClick = null }
    }

    Canvas(
        modifier = Modifier
            .size(20.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = description },
    ) {
        val radius = size.minDimension / 2f * 0.6f
        when (current) {
            is DotState.Checking ->
                drawCircle(color = Color.Gray, radius = radius, style = Stroke(width = 1.5.dp.toPx()))
            is DotState.UpToDate ->
                drawCircle(color = GREEN, radius = radius)
            is DotState.Available -> {
                drawCircle(color = AMBER, radius = radius)
                drawDownloadGlyph(radius)
            }
            is DotState.Downloading -> {
                drawCircle(color = Color.Gray, radius = radius, style = Stroke(width = 1.5.dp.toPx()))
                drawArc(
                    color = AMBER,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx()),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }
            is DotState.Ready -> {
                drawCircle(color = GREEN, radius = radius)
                drawInstallGlyph(radius)
            }
            is DotState.Unreachable ->
                drawCircle(color = RED, radius = radius)
        }
    }
}

/** A small downward arrow: stem plus a chevron head. */
private fun DrawScope.drawDownloadGlyph(radius: Float) {
    val c = center
    val half = radius * 0.45f
    drawLine(Color.White, Offset(c.x, c.y - half), Offset(c.x, c.y + half * 0.3f), strokeWidth = 1.5.dp.toPx())
    val path = Path().apply {
        moveTo(c.x - half * 0.6f, c.y - half * 0.15f)
        lineTo(c.x, c.y + half * 0.55f)
        lineTo(c.x + half * 0.6f, c.y - half * 0.15f)
    }
    drawPath(path, color = Color.White, style = Stroke(width = 1.5.dp.toPx()))
}

/** A small checkmark, reused from ChktIcon.Tick's proportions. */
private fun DrawScope.drawInstallGlyph(radius: Float) {
    val c = center
    val half = radius * 0.5f
    val path = Path().apply {
        moveTo(c.x - half, c.y)
        lineTo(c.x - half * 0.25f, c.y + half * 0.6f)
        lineTo(c.x + half, c.y - half * 0.6f)
    }
    drawPath(path, color = Color.White, style = Stroke(width = 1.5.dp.toPx()))
}
