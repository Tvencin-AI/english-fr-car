package com.englishdrive.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.englishdrive.learning.LearningSession

/**
 * Android Auto dashboard screen.
 *
 * Shows:
 *  - Emma's last message
 *  - Current level (N/100) and band
 *  - French support notice for levels 1–5
 *  - Start/Stop, Level−, Level+ buttons
 *  - Session state header
 */
class LearningCarScreen(carContext: CarContext) : Screen(carContext) {

    private val session = LearningSession(carContext)

    private var headerText = "English Drive"
    private var bodyText   = "Press Start to begin your English lesson with Emma."
    private var isRunning  = false

    init {
        session.onEmmaMessage = { text ->
            bodyText = "Emma: $text"
            invalidate()
        }
        session.onStudentMessage = { text ->
            bodyText = "You: \"$text\""
            invalidate()
        }
        session.onSessionStateChanged = { state ->
            headerText = when (state) {
                LearningSession.SessionState.IDLE               -> "English Drive"
                LearningSession.SessionState.STARTING           -> "Starting…"
                LearningSession.SessionState.EMMA_SPEAKING      -> "Emma is speaking"
                LearningSession.SessionState.WAITING_FOR_STUDENT,
                LearningSession.SessionState.STUDENT_SPEAKING   -> "Listening — speak now"
                LearningSession.SessionState.PROCESSING         -> "Emma is thinking…"
                LearningSession.SessionState.PAUSED             -> "Paused"
                LearningSession.SessionState.ERROR              -> "Error"
            }
            invalidate()
        }
        session.onError = { error ->
            CarToast.makeText(carContext, error, CarToast.LENGTH_SHORT).show()
            bodyText = "Error: $error"
            invalidate()
        }
        session.onLevelChanged = { level ->
            CarToast.makeText(
                carContext,
                "Level ${level.number}/100 — ${level.shortName}",
                CarToast.LENGTH_SHORT
            ).show()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val level = session.levelManager.currentLevel

        val startStop = Action.Builder()
            .setTitle(if (isRunning) "Stop" else "Start")
            .setOnClickListener {
                if (isRunning) {
                    session.stopSession()
                    isRunning = false
                    bodyText = "Session stopped. Press Start to begin a new lesson."
                } else {
                    if (!session.geminiManager.hasApiKey()) {
                        CarToast.makeText(
                            carContext,
                            "Set your Gemini API key in the phone app first.",
                            CarToast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }
                    isRunning = true
                    session.startSession()
                }
                invalidate()
            }
            .build()

        val levelUp = Action.Builder()
            .setTitle("Level +")
            .setOnClickListener {
                session.levelUp()
                invalidate()
            }
            .build()

        val levelDown = Action.Builder()
            .setTitle("Level −")
            .setOnClickListener {
                session.levelDown()
                invalidate()
            }
            .build()

        // Build body with level info
        val frenchNote = if (level.useFrenchHelp) "\n🇫🇷 French explanations active" else ""
        val fullBody = buildString {
            appendLine(bodyText)
            appendLine()
            append("Level ${level.number}/100  ·  ${level.shortName}")
            append(frenchNote)
        }

        return MessageTemplate.Builder(fullBody)
            .setTitle(headerText)
            .addAction(startStop)
            .addAction(levelUp)
            .addAction(levelDown)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    fun cleanup() {
        session.release()
    }
}
