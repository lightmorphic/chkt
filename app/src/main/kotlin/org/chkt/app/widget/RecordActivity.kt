package org.chkt.app.widget

import android.Manifest
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
 * The tap-to-record flow: tap the widget → this small overlay appears and
 * listens → tap Stop (or just finish speaking) → the phrase becomes a
 * reminder. Speech recognition runs through whatever recognition service the
 * phone has installed; audio never goes anywhere Chkt controls.
 */
class RecordActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private val state = mutableStateOf<UiState>(UiState.Idle)
    private val scope = MainScope()

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
        else state.value = UiState.Problem("Chkt needs microphone access to hear the reminder.")
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

    private fun beginCapture() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            state.value = UiState.Problem(
                "No speech recognition service is installed on this phone. " +
                    "Install one (e.g. FUTO Voice Input from F-Droid) and try again — " +
                    "or add reminders by hand in the app."
            )
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
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
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
                    state.value = UiState.Problem(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Didn't catch that."
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
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            )
        }
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
                val list = repo.ensureDefaultList()
                repo.saveReminder(
                    Reminder(
                        listId = list.id,
                        title = parsed.title.replaceFirstChar(Char::uppercase),
                        dueAt = parsed.dueAt.toInstant().toEpochMilli(),
                        repeatRule = parsed.repeat.encode(),
                    )
                )
                "“${parsed.title}” — ${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM HH:mm").format(parsed.dueAt)}"
            }
            state.value = UiState.Saved(summary)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
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
