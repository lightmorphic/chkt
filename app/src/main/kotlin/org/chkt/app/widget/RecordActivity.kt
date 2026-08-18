package org.chkt.app.widget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chkt.app.data.Repository
import org.chkt.app.domain.PhraseParser
import org.chkt.app.data.Reminder
import org.chkt.app.ui.theme.ChktTheme
import java.time.ZonedDateTime

/**
 * The tap-to-record flow: tap the widget → listens → the phrase becomes a
 * reminder. Speech recognition runs through whatever the phone has: first
 * tried as a bound RecognitionService (SpeechRecognizer — how Google's
 * speech services and similar work), falling back to launching whatever
 * app handles the RECOGNIZE_SPEECH intent as an activity (how FUTO Voice
 * Input and other privacy-focused, RecognitionService-less recognizers
 * work — common on GrapheneOS and similar with no Google services). Audio
 * never goes anywhere CHKT controls either way.
 */
class RecordActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private val state = mutableStateOf<UiState>(UiState.Idle)
    private val scope = MainScope()

    companion object {
        const val NO_RECOGNIZER_MESSAGE =
            "No working speech recognition service on this phone. " +
                "Install one (e.g. FUTO Voice Input from F-Droid) and try again, " +
                "or add reminders by hand in the app."
    }

    sealed class UiState {
        object Idle : UiState()
        object Listening : UiState()
        data class Heard(val text: String) : UiState()
        data class Saved(val summary: String) : UiState()
        data class Problem(val message: String) : UiState()
    }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else state.value = UiState.Problem("CHKT needs microphone access to hear the reminder.")
    }

    private val activityRecognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        RecordWidgetReceiver.setActive(this, active = false)
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (result.resultCode != Activity.RESULT_OK || text.isNullOrBlank()) {
            state.value = UiState.Problem("Didn't catch that.")
        } else {
            state.value = UiState.Heard(text)
            saveParsed(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChktTheme {
                RecordSheet(
                    state = state.value,
                    onStop = { recognizer?.stopListening() },
                    onClose = { finish() },
                    onRetry = { beginCapture() },
                )
            }
        }
        beginCapture()
    }

    private fun recognizeIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

    private fun beginCapture() {
        val hasBoundService = SpeechRecognizer.isRecognitionAvailable(this)
        val hasRecognizerActivity = recognizeIntent().resolveActivity(packageManager) != null
        if (!hasBoundService && !hasRecognizerActivity) {
            state.value = UiState.Problem(NO_RECOGNIZER_MESSAGE)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        state.value = UiState.Listening
        RecordWidgetReceiver.setActive(this, active = true)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            startViaRecognitionService()
        } else {
            startViaRecognizerActivity()
        }
    }

    /** A bound RecognitionService (e.g. Google's speech services): CHKT
     *  drives listening directly and shows its own "Listening…" overlay. */
    private fun startViaRecognitionService() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    RecordWidgetReceiver.setActive(this@RecordActivity, active = false)
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        state.value = UiState.Problem("Didn't catch that.")
                    } else {
                        state.value = UiState.Heard(text)
                        saveParsed(text)
                    }
                }

                override fun onError(error: Int) {
                    // ERROR_CLIENT: isRecognitionAvailable() can return true even with
                    // no working service actually bound (seen on GrapheneOS) — the
                    // component resolves, but there's nothing real behind it, so it
                    // fails the instant listening starts. Try the activity-based
                    // fallback instead of giving up, since a recognizer app that
                    // only supports that path (e.g. FUTO Voice Input) may still work.
                    if (error == SpeechRecognizer.ERROR_CLIENT && recognizeIntent().resolveActivity(packageManager) != null) {
                        startViaRecognizerActivity()
                        return
                    }
                    RecordWidgetReceiver.setActive(this@RecordActivity, active = false)
                    state.value = UiState.Problem(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Didn't catch that."
                            SpeechRecognizer.ERROR_CLIENT -> NO_RECOGNIZER_MESSAGE
                            else -> "Speech recognition failed (code $error)."
                        }
                    )
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(recognizeIntent())
        }
    }

    /** No bound RecognitionService: launch whatever app handles the
     *  RECOGNIZE_SPEECH intent as its own activity (FUTO Voice Input and
     *  similar). That app shows its own listening UI and hands results
     *  back once the user finishes speaking. */
    private fun startViaRecognizerActivity() {
        val intent = recognizeIntent()
        if (intent.resolveActivity(packageManager) == null) {
            RecordWidgetReceiver.setActive(this, active = false)
            state.value = UiState.Problem(NO_RECOGNIZER_MESSAGE)
            return
        }
        activityRecognizer.launch(intent)
    }

    private fun saveParsed(text: String) {
        val parsed = PhraseParser.parse(text, ZonedDateTime.now())
        if (parsed == null) {
            state.value = UiState.Problem(
                "Heard “$text” but couldn't work out a time. " +
                    "Try the shape: “remind me at 2pm to feed the cat”."
            )
            return
        }
        scope.launch {
            val summary = withContext(Dispatchers.IO) {
                val repo = Repository(applicationContext)
                repo.saveReminder(
                    Reminder(
                        title = parsed.title.replaceFirstChar(Char::uppercase),
                        dueAt = parsed.dueAt.toInstant().toEpochMilli(),
                        repeatRule = parsed.repeat.encode(),
                    )
                )
                "“${parsed.title}”, ${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM HH:mm").format(parsed.dueAt)}"
            }
            state.value = UiState.Saved(summary)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        // Safety net: covers Stop being tapped mid-listen, or the activity
        // being torn down before a result/error callback ever arrives.
        RecordWidgetReceiver.setActive(this, active = false)
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun RecordSheet(
    state: RecordActivity.UiState,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state) {
                    is RecordActivity.UiState.Idle -> Text("Starting…")
                    is RecordActivity.UiState.Listening -> {
                        Text("Listening…", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Say: “remind me at 2pm to feed the cat”",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onStop) { Text("Stop") }
                    }
                    is RecordActivity.UiState.Heard -> Text("Heard: “${state.text}”")
                    is RecordActivity.UiState.Saved -> {
                        Text("Reminder saved", style = MaterialTheme.typography.titleLarge)
                        Text(state.summary, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onClose) { Text("Close") }
                    }
                    is RecordActivity.UiState.Problem -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onRetry) { Text("Try again") }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                }
            }
        }
    }
}
