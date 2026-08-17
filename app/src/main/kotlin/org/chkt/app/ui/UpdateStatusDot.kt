package org.chkt.app.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.chkt.app.update.Updater
import java.io.File

/**
 * Update-status dot for the home screen top bar. Green is the resting state
 * and doubles as a manual "check now" button: tapping it pulses while it
 * checks, then either turns yellow (update found) or pulses back down to
 * green (already current). Tapping yellow downloads; once the download
 * lands the dot turns blue and a dialog offers to install. Red means the
 * last check couldn't reach GitHub at all.
 */
private enum class DotColor { GREEN, YELLOW, BLUE, RED }

private val GREEN = Color(0xFF4BAE4F)
private val YELLOW = Color(0xFFFFC006)
private val BLUE = Color(0xFF2E6FE8)
private val RED = Color(0xFFF34236)

@Composable
fun UpdateStatusDot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var color by remember { mutableStateOf(DotColor.GREEN) }
    var pulsing by remember { mutableStateOf(false) }
    var pendingInfo by remember { mutableStateOf<Updater.UpdateInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }

    suspend fun check() {
        when (val result = Updater.check(context)) {
            is Updater.CheckResult.UpToDate -> color = DotColor.GREEN
            is Updater.CheckResult.UpdateAvailable -> {
                pendingInfo = result.info
                color = DotColor.YELLOW
            }
            is Updater.CheckResult.Failed -> color = DotColor.RED
        }
    }

    // Silent check on open: no pulse, so the dot doesn't animate on every
    // launch — pulsing is reserved for a check the user actually asked for.
    LaunchedEffect(Unit) { check() }

    val description: String
    val onClick: (() -> Unit)?
    when (color) {
        DotColor.GREEN -> {
            description = "Up to date, tap to check for updates"
            onClick = {
                pulsing = true
                scope.launch {
                    check()
                    pulsing = false
                }
            }
        }
        DotColor.YELLOW -> {
            description = "Update available, tap to download"
            onClick = {
                val info = pendingInfo
                if (info != null) {
                    pulsing = true
                    scope.launch {
                        when (val result = Updater.downloadUpdate(context, info)) {
                            is Updater.DownloadResult.Ok -> {
                                downloadedApk = result.apk
                                color = DotColor.BLUE
                                showInstallDialog = true
                            }
                            is Updater.DownloadResult.Failed -> { /* stays yellow, retry available */ }
                        }
                        pulsing = false
                    }
                }
            }
        }
        DotColor.BLUE -> {
            description = "Downloaded, tap to install"
            onClick = { showInstallDialog = true }
        }
        DotColor.RED -> {
            description = "Can't reach GitHub"
            onClick = null
        }
    }

    val transition = rememberInfiniteTransition(label = "update-dot-pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "update-dot-pulse-scale",
    )

    Canvas(
        modifier = Modifier
            .size(20.dp)
            .scale(if (pulsing) pulseScale else 1f)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = description },
    ) {
        val radius = size.minDimension / 2f * 0.6f
        val dotColor = when (color) {
            DotColor.GREEN -> GREEN
            DotColor.YELLOW -> YELLOW
            DotColor.BLUE -> BLUE
            DotColor.RED -> RED
        }
        drawCircle(color = dotColor, radius = radius)
    }

    if (showInstallDialog) {
        val apk = downloadedApk
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text("Update downloaded") },
            text = { Text("The new version is ready. Install it now?") },
            confirmButton = {
                TextButton(onClick = {
                    showInstallDialog = false
                    apk?.let { Updater.install(context, it) }
                }) { Text("Install") }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDialog = false }) { Text("Later") }
            },
        )
    }
}
