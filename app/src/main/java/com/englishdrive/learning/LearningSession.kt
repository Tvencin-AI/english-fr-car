package com.englishdrive.learning

import android.content.Context
import com.englishdrive.speech.SpeechManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Orchestrates the full voice learning loop.
 *
 * Flow:
 *   Emma speaks  →  mic opens  →  student speaks
 *     → VoiceCommandParser checks for commands
 *       → command  : handle locally, no API call
 *       → speech   : Gemini processes → Emma replies → loop
 *
 * Buttons in the UI call [handleExternalCommand] directly.
 */
class LearningSession(private val context: Context) {

    // ── Dependencies ──────────────────────────────────────────────────────────

    val speechManager = SpeechManager(context)
    val geminiManager = GeminiManager(context)
    val levelManager  = LevelManager(context)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── State ─────────────────────────────────────────────────────────────────

    enum class SessionState {
        IDLE, STARTING, EMMA_SPEAKING,
        WAITING_FOR_STUDENT, STUDENT_SPEAKING,
        PROCESSING, PAUSED, ERROR
    }

    var sessionState: SessionState = SessionState.IDLE
        private set

    var onSessionStateChanged  : ((SessionState) -> Unit)?       = null
    var onEmmaMessage          : ((String) -> Unit)?             = null
    var onStudentMessage       : ((String) -> Unit)?             = null
    var onError                : ((String) -> Unit)?             = null
    var onLevelChanged         : ((LevelManager.Level) -> Unit)? = null

    private var isFirstMessage  = true
    private var isSessionActive = false
    private var lastEmmaMessage = ""

    // ── Session control ───────────────────────────────────────────────────────

    fun startSession() {
        if (!geminiManager.hasApiKey()) {
            onError?.invoke("Please add your Gemini API key in settings.")
            return
        }
        isSessionActive = true
        isFirstMessage  = true
        lastEmmaMessage = ""
        geminiManager.resetConversation()
        levelManager.incrementSession()

        setState(SessionState.STARTING)
        setupSpeechCallbacks()
        sendToGemini("__START__")
    }

    fun pauseSession() {
        speechManager.stopSpeaking()
        speechManager.stopListening()
        isSessionActive = false
        setState(SessionState.PAUSED)
    }

    fun resumeSession() {
        isSessionActive = true
        emmaSpeak("Right, let's carry on. Whenever you're ready.") {
            startListening()
        }
    }

    fun stopSession() {
        isSessionActive = false
        speechManager.stopSpeaking()
        speechManager.stopListening()
        geminiManager.resetConversation()
        setState(SessionState.IDLE)
    }

    // ── Level navigation (called by buttons or voice) ─────────────────────────

    fun levelUp() {
        if (levelManager.levelUp()) {
            onLevelChanged?.invoke(levelManager.currentLevel)
            geminiManager.resetConversation()
            isFirstMessage = false
            if (isSessionActive) {
                sendToGemini(
                    "The student has just moved up to level ${levelManager.currentLevelNumber}/100 " +
                    "(${levelManager.currentLevel.band.cefrApprox}). " +
                    "Congratulate them briefly and immediately start teaching at this new level."
                )
            }
        } else {
            if (isSessionActive) {
                emmaSpeak("You're already at level 100 — native fluency! Absolutely brilliant.") {
                    startListening()
                }
            }
        }
    }

    fun levelDown() {
        if (levelManager.levelDown()) {
            onLevelChanged?.invoke(levelManager.currentLevel)
            geminiManager.resetConversation()
            isFirstMessage = false
            if (isSessionActive) {
                sendToGemini(
                    "The student moved back to level ${levelManager.currentLevelNumber}/100 " +
                    "(${levelManager.currentLevel.band.cefrApprox}). " +
                    "Be kind and gently restart at this level."
                )
            }
        } else {
            if (isSessionActive) {
                emmaSpeak("You're already at level 1. We'll build from here, no worries!") {
                    startListening()
                }
            }
        }
    }

    fun changeLevel(n: Int) {
        levelManager.setLevel(n)
        onLevelChanged?.invoke(levelManager.currentLevel)
        geminiManager.resetConversation()
        isFirstMessage = false
        if (isSessionActive) {
            sendToGemini(
                "The student has changed to level ${levelManager.currentLevelNumber}/100 " +
                "(${levelManager.currentLevel.band.cefrApprox}). " +
                "Acknowledge warmly and adapt immediately."
            )
        }
    }

    // ── External command from UI buttons ──────────────────────────────────────

    /**
     * Called by MainActivity buttons.
     * @param cmd  "repeat" | "slower" | "stats" | "levelup" | "leveldown"
     */
    fun handleExternalCommand(cmd: String) {
        if (!isSessionActive) return
        when (cmd) {
            "repeat" -> {
                speechManager.stopListening()
                if (lastEmmaMessage.isNotBlank()) {
                    emmaSpeak(lastEmmaMessage) { startListening() }
                } else {
                    emmaSpeak("I haven't said anything yet! Let's begin.") { startListening() }
                }
            }
            "slower" -> {
                // Speech rate is already adjusted by MainActivity before calling here
                emmaSpeak("Of course — I'll speak a little more slowly.") { startListening() }
            }
            "stats" -> {
                val level = levelManager.currentLevel
                val msg   = "You're currently at level ${level.number} out of 100. " +
                            "${level.shortName}. ${level.band.teachingStyle.lines().first()}."
                emmaSpeak(msg) { startListening() }
            }
            "levelup"   -> levelUp()
            "leveldown" -> levelDown()
        }
    }

    // ── Speech recognition callbacks ──────────────────────────────────────────

    private fun setupSpeechCallbacks() {
        speechManager.onSpeechResult = { text ->
            if (isSessionActive) handleStudentInput(text)
        }

        speechManager.onError = { error ->
            if (!isSessionActive) return@onError
            when (error) {
                "no_match" -> emmaSpeak(
                    "I'm sorry, I didn't quite catch that. Could you say that again, please?"
                ) { startListening() }

                "speech_timeout" -> emmaSpeak(
                    "Take your time — I'm right here whenever you're ready."
                ) { startListening() }

                else -> { setState(SessionState.ERROR); onError?.invoke(error) }
            }
        }
    }

    // ── Input routing ─────────────────────────────────────────────────────────

    private fun handleStudentInput(text: String) {
        onStudentMessage?.invoke(text)

        when (val cmd = VoiceCommandParser.parse(text)) {
            is VoiceCommandParser.Command.SetLevel -> {
                changeLevel(cmd.levelNumber)
                val ack = "Switching to level ${cmd.levelNumber}. I'll adapt straightaway."
                emmaSpeak(ack) { /* changeLevel triggers its own flow */ }
            }
            VoiceCommandParser.Command.LevelUp      -> levelUp()
            VoiceCommandParser.Command.LevelDown    -> levelDown()
            VoiceCommandParser.Command.StopSession  -> {
                emmaSpeak("Alright, we'll stop here. Great work today. See you next time!") {
                    stopSession()
                }
            }
            VoiceCommandParser.Command.PauseSession -> {
                emmaSpeak("Pausing now. Just say 'resume' when you're ready.") { pauseSession() }
            }
            VoiceCommandParser.Command.RepeatLast   -> {
                if (lastEmmaMessage.isNotBlank()) {
                    emmaSpeak(lastEmmaMessage) { startListening() }
                } else {
                    emmaSpeak("I haven't said anything yet! Let's get going.") { startListening() }
                }
            }
            VoiceCommandParser.Command.AskStats     -> {
                val level = levelManager.currentLevel
                emmaSpeak(
                    "You're at level ${level.number} out of 100. ${level.shortName}."
                ) { startListening() }
            }
            VoiceCommandParser.Command.SpeakSlower  -> {
                speechManager.setSpeechRate(0.78f)
                emmaSpeak("Of course, I'll speak a bit more slowly.") { startListening() }
            }
            VoiceCommandParser.Command.ResumeSession -> resumeSession()
            VoiceCommandParser.Command.None          -> {
                // Regular speech → Gemini
                levelManager.incrementExchange()
                sendToGemini(text)
            }
        }
    }

    // ── Gemini call ───────────────────────────────────────────────────────────

    private fun sendToGemini(raw: String) {
        setState(SessionState.PROCESSING)

        val msg = if (raw == "__START__") {
            null // isFirstMessage=true will trigger the start instruction inside GeminiManager
        } else raw

        scope.launch {
            val reply = geminiManager.sendMessage(
                userMessage    = msg ?: "Hello",
                level          = levelManager.currentLevel,
                isFirstMessage = isFirstMessage
            )
            isFirstMessage = false

            if (isSessionActive) {
                lastEmmaMessage = reply
                onEmmaMessage?.invoke(reply)
                emmaSpeak(reply) { if (isSessionActive) startListening() }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun emmaSpeak(text: String, onDone: () -> Unit) {
        setState(SessionState.EMMA_SPEAKING)
        speechManager.speak(text, onDone)
    }

    private fun startListening() {
        if (!isSessionActive) return
        setState(SessionState.WAITING_FOR_STUDENT)
        scope.launch {
            kotlinx.coroutines.delay(600)
            if (isSessionActive) {
                setState(SessionState.STUDENT_SPEAKING)
                speechManager.startListening()
            }
        }
    }

    private fun setState(s: SessionState) {
        sessionState = s
        onSessionStateChanged?.invoke(s)
    }

    fun release() { stopSession(); speechManager.release() }
}
