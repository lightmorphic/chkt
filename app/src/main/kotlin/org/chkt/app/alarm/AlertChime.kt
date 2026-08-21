package org.chkt.app.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Plays the alert's notification sound and, unlike a notification channel's
 * own sound, says when it has finished — so the spoken reminder can follow
 * the ding instead of talking over it.
 *
 * Owning playback also makes every alert of a reminder sound the same. A
 * channel only sounds when a notification first appears: nag re-alerts and
 * post-snooze alerts reuse the same still-visible notification, so their
 * ding was swallowed.
 */
class AlertChime(private val onEvent: (String) -> Unit = {}) {
    private var player: MediaPlayer? = null
    private var pending: CancellableContinuation<Unit>? = null

    /**
     * Plays the sound, suspending until it ends — or until [MAX_MS], so a
     * long or looping ringtone can't hold the voice back indefinitely.
     *
     * A chosen sound that won't open (a picked file whose read permission
     * didn't survive, say) falls back to the system default notification
     * sound rather than skipping the ding: the ding IS the alert cue, and
     * which ding matters less than whether it happens.
     */
    suspend fun play(context: Context, respectDnd: Boolean) {
        val uri = Notifications.soundUri(context) ?: return
        val fallback = android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_NOTIFICATION)
        val finished = playUri(context, uri, respectDnd)
        if (!finished && uri != fallback) {
            onEvent("chime retry with default sound")
            playUri(context, fallback, respectDnd)
        }
    }

    /** True if the sound genuinely played (or timed out mid-play); false if
     * it never got started, which is when a fallback is worth trying. */
    private suspend fun playUri(context: Context, uri: android.net.Uri, respectDnd: Boolean): Boolean {
        var started = false
        val completed = withTimeoutOrNull(MAX_MS) {
            suspendCancellableCoroutine { cont ->
                pending = cont
                cont.invokeOnCancellation { releasePlayer() }
                val mp = MediaPlayer()
                player = mp
                try {
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            // Matches the channel each mode posts to: alarm
                            // usage cuts through Do Not Disturb, notification
                            // usage lets DND keep things quiet.
                            .setUsage(
                                if (respectDnd) AudioAttributes.USAGE_NOTIFICATION
                                else AudioAttributes.USAGE_ALARM
                            )
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.setDataSource(context, uri)
                    mp.setOnCompletionListener { onEvent("chime completed"); finish() }
                    mp.setOnErrorListener { _, what, extra ->
                        onEvent("chime player error $what/$extra"); finish(); true
                    }
                    mp.setOnPreparedListener {
                        started = true; onEvent("chime playing"); it.start()
                    }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    // Unreadable or revoked sound URI, or a media server that
                    // won't play ball.
                    onEvent("chime open failed: ${e.javaClass.simpleName}")
                    finish()
                }
            }
        }
        if (completed == null) onEvent(if (started) "chime cut at ${MAX_MS}ms" else "chime never started (${MAX_MS}ms)")
        releasePlayer()
        return started
    }

    /** Cuts the sound short — the user answered, or a newer alert took over. */
    fun stop() {
        releasePlayer()
        finish()
    }

    private fun releasePlayer() {
        val mp = player ?: return
        player = null
        runCatching { mp.stop() }
        mp.release()
    }

    private fun finish() {
        val cont = pending ?: return
        pending = null
        if (cont.isActive) cont.resume(Unit)
    }

    companion object {
        private const val MAX_MS = 8_000L
    }
}
