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
class AlertChime {
    private var player: MediaPlayer? = null
    private var pending: CancellableContinuation<Unit>? = null

    /**
     * Plays the sound, suspending until it ends — or until [MAX_MS], so a
     * long or looping ringtone can't hold the voice back indefinitely.
     */
    suspend fun play(context: Context, respectDnd: Boolean) {
        val uri = Notifications.soundUri(context) ?: return
        withTimeoutOrNull(MAX_MS) {
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
                            // usage lets DND silence it.
                            .setUsage(
                                if (respectDnd) AudioAttributes.USAGE_NOTIFICATION
                                else AudioAttributes.USAGE_ALARM
                            )
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.setDataSource(context, uri)
                    mp.setOnCompletionListener { finish() }
                    mp.setOnErrorListener { _, _, _ -> finish(); true }
                    mp.setOnPreparedListener { it.start() }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    // Unreadable or revoked sound URI, or a media server that
                    // won't play ball: go straight on to the voice.
                    finish()
                }
            }
        }
        releasePlayer()
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
