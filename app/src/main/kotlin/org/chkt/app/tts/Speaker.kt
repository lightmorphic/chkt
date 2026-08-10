package org.chkt.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Thin wrapper over Android's system TTS. Chkt never bundles a voice engine,
 * it uses whatever engine the user has installed (Sherpa TTS recommended).
 */
class Speaker(
    context: Context,
    private val respectDnd: Boolean = false,
    private val onReady: (Boolean) -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        // Alarm stream cuts through Do Not Disturb; the
                        // notification stream lets DND keep things quiet.
                        .setUsage(if (respectDnd) AudioAttributes.USAGE_NOTIFICATION else AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            onReady(ready)
        }
    }

    fun speak(text: String, utteranceId: String, onDone: () -> Unit) {
        val engine = tts
        if (!ready || engine == null) { onDone(); return }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id == utteranceId) onDone() }
            @Deprecated("Deprecated in API 21")
            override fun onError(id: String?) { if (id == utteranceId) onDone() }
            override fun onError(id: String?, errorCode: Int) { if (id == utteranceId) onDone() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        /** True if any TTS engine is installed on the device. */
        fun engineInstalled(context: Context): Boolean {
            val probe = TextToSpeech(context) { }
            val has = probe.engines.isNotEmpty()
            probe.shutdown()
            return has
        }

        const val SHERPA_FDROID_URL = "https://f-droid.org/packages/org.woheller69.ttsengine/"
    }
}
