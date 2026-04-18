package com.englishdrive.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Manages British English Text-to-Speech and Speech Recognition.
 *
 * TTS: en-GB, speech rate adjustable via [setSpeechRate].
 * STT: en-GB, auto-detects silence and returns transcription via [onSpeechResult].
 */
class SpeechManager(private val context: Context) {

    // ── State ─────────────────────────────────────────────────────────────────

    enum class State { IDLE, SPEAKING, LISTENING, PROCESSING }

    var state: State = State.IDLE
        private set

    var onStateChanged  : ((State) -> Unit)?  = null
    var onSpeechResult  : ((String) -> Unit)? = null
    var onError         : ((String) -> Unit)? = null

    // ── TTS ───────────────────────────────────────────────────────────────────

    private var tts      : TextToSpeech? = null
    private var ttsReady = false

    /** Current speech rate. Starts at 0.92 (slightly slower than default). */
    private var currentSpeechRate = 0.92f

    private val utteranceCallbacks = mutableMapOf<String, () -> Unit>()

    init { initTts() }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Prefer en-GB; fall back gracefully
                val gbLocale = Locale("en", "GB")
                val result = tts?.setLanguage(gbLocale)
                ttsReady = (result != TextToSpeech.LANG_MISSING_DATA &&
                            result != TextToSpeech.LANG_NOT_SUPPORTED)
                if (!ttsReady) {
                    tts?.setLanguage(Locale.UK)
                    ttsReady = true
                }
                tts?.setSpeechRate(currentSpeechRate)
                tts?.setPitch(1.0f)
                applyUtteranceListener()
            }
        }
    }

    /**
     * Adjust TTS speech rate.
     * @param rate  1.0 = normal, 0.8 = slower, 1.2 = faster.
     */
    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 1.5f)
        tts?.setSpeechRate(currentSpeechRate)
    }

    /**
     * Speak [text] using British English TTS.
     * [onDone] is called when speech finishes (or if an error occurs).
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) {
            onError?.invoke("Text-to-speech is not ready yet.")
            onDone?.invoke()
            return
        }

        tts?.stop()
        utteranceCallbacks.clear()

        // Strip any accidental markdown characters
        val cleanText = text
            .replace(Regex("[*#_`]"), "")
            .replace(Regex("\\[.*?]"), "")
            .trim()

        val uid = UUID.randomUUID().toString()
        utteranceCallbacks[uid] = onDone ?: {}

        applyUtteranceListener()

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid)
        }

        setState(State.SPEAKING)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, uid)
    }

    fun stopSpeaking() {
        tts?.stop()
        utteranceCallbacks.clear()
        setState(State.IDLE)
    }

    private fun applyUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(uid: String?)  { setState(State.SPEAKING) }
            override fun onDone(uid: String?)   {
                setState(State.IDLE)
                uid?.let { utteranceCallbacks.remove(it)?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(uid: String?)  {
                setState(State.IDLE)
                uid?.let { utteranceCallbacks.remove(it)?.invoke() }
                onError?.invoke("TTS error")
            }
        })
    }

    // ── Speech Recognition ────────────────────────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Start listening for the student's speech.
     * Results are delivered asynchronously via [onSpeechResult] or [onError].
     */
    fun startListening() {
        if (state == State.LISTENING) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError?.invoke("Speech recognition is not available on this device.")
            return
        }

        setState(State.LISTENING)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-GB")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { setState(State.LISTENING) }
            override fun onBeginningOfSpeech()             {}
            override fun onRmsChanged(rms: Float)          {}
            override fun onBufferReceived(buf: ByteArray?) {}
            override fun onEndOfSpeech()                   { setState(State.PROCESSING) }

            override fun onError(error: Int) {
                setState(State.IDLE)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO                 -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT                -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                    SpeechRecognizer.ERROR_NETWORK               -> "Network error – check your connection"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT       -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH              -> "no_match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY       -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER                -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT        -> "speech_timeout"
                    else -> "Speech recognition error ($error)"
                }
                onError?.invoke(msg)
            }

            override fun onResults(results: Bundle?) {
                setState(State.IDLE)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!text.isNullOrBlank()) {
                    onSpeechResult?.invoke(text)
                } else {
                    onError?.invoke("no_match")
                }
            }

            override fun onPartialResults(partial: Bundle?)    {}
            override fun onEvent(type: Int, params: Bundle?)   {}
        })

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        setState(State.IDLE)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        utteranceCallbacks.clear()
        setState(State.IDLE)
    }

    private fun setState(new: State) {
        if (state != new) {
            state = new
            onStateChanged?.invoke(new)
        }
    }
}
