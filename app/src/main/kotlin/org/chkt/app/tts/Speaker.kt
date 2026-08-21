package org.chkt.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/**
 * Thin wrapper over Android's system TTS. CHKT never bundles a voice engine,
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
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        private const val SHERPA_PACKAGE = "org.woheller69.ttsengine"
        const val SHERPA_FDROID_URL = "https://f-droid.org/packages/org.woheller69.ttsengine/"

        private fun installedEnginePackages(context: Context): List<String> {
            val probe = TextToSpeech(context) { }
            val packages = probe.engines.map { it.name }
            probe.shutdown()
            return packages
        }

        /** True unless neither Google's TTS nor Sherpa TTS is present — the
         * two engines CHKT knows will actually speak reminders out loud.
         * Devices without Google Play Services (GrapheneOS etc.) commonly
         * lack the former, so this is what decides whether to nudge toward
         * installing Sherpa, not just "is anything at all installed". */
        fun shouldRecommendSherpa(context: Context): Boolean {
            val packages = installedEnginePackages(context)
            return GOOGLE_TTS_PACKAGE !in packages && SHERPA_PACKAGE !in packages
        }
    }
}
