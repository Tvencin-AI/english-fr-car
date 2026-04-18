package com.englishdrive

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.englishdrive.databinding.ActivityMainBinding
import com.englishdrive.learning.LearningSession
import com.englishdrive.learning.SessionStats

class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding
    private lateinit var session : LearningSession
    private lateinit var stats  : SessionStats

    private var isSessionActive   = false
    private var isPaused          = false
    private var micPulse          : AnimatorSet? = null
    private var speechRateIndex   = 0
    private val speechRates       = floatArrayOf(0.92f, 0.78f, 0.65f)  // Normal → Slow → Very slow

    companion object { private const val REQ_MIC = 100 }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = LearningSession(this)
        stats   = SessionStats(this)

        setupCallbacks()
        setupButtons()
        refreshLevelUI()

        if (!session.geminiManager.hasApiKey()) showApiKeyDialog()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMicPulse()
        stats.stopTimer()
        session.release()
    }

    // ── Callbacks from LearningSession ────────────────────────────────────────

    private fun setupCallbacks() {

        session.onEmmaMessage = { text ->
            runOnUiThread {
                appendMessage("Emma 🇬🇧", text, isEmma = true)
                setStatus("🔊 Emma parle…")
            }
        }

        session.onStudentMessage = { text ->
            runOnUiThread {
                appendMessage("Vous", text, isEmma = false)
                setStatus("💭 Emma réfléchit…")
            }
        }

        session.onSessionStateChanged = { state ->
            runOnUiThread {
                val listening = state == LearningSession.SessionState.STUDENT_SPEAKING ||
                                state == LearningSession.SessionState.WAITING_FOR_STUDENT
                if (listening) startMicPulse() else stopMicPulse()

                setStatus(when (state) {
                    LearningSession.SessionState.IDLE               -> "Prêt — appuyez sur Démarrer"
                    LearningSession.SessionState.STARTING           -> "Démarrage…"
                    LearningSession.SessionState.EMMA_SPEAKING      -> "🔊 Emma parle…"
                    LearningSession.SessionState.WAITING_FOR_STUDENT,
                    LearningSession.SessionState.STUDENT_SPEAKING   -> "🎤 À vous — parlez maintenant"
                    LearningSession.SessionState.PROCESSING         -> "💭 Emma réfléchit…"
                    LearningSession.SessionState.PAUSED             -> "⏸ En pause"
                    LearningSession.SessionState.ERROR              -> "⚠ Erreur"
                })
            }
        }

        session.onError = { error ->
            runOnUiThread {
                if (error.contains("API", ignoreCase = true)) showApiKeyDialog()
                else Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }

        session.onLevelChanged = { _ ->
            runOnUiThread { refreshLevelUI() }
        }
    }

    // ── Button wiring ─────────────────────────────────────────────────────────

    private fun setupButtons() {

        // ── Main: Start / Stop ─────────────────────────────────────────────
        binding.btnStartStop.setOnClickListener {
            if (!isSessionActive) doStart() else doStop()
        }

        // ── Main: Clear conversation ───────────────────────────────────────
        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Effacer la conversation ?")
                .setMessage("L'historique de la conversation sera supprimé.")
                .setPositiveButton("Effacer") { _, _ ->
                    binding.tvConversation.text = ""
                    session.geminiManager.resetConversation()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        // ── Settings: API key ──────────────────────────────────────────────
        binding.btnSettings.setOnClickListener { showApiKeyDialog() }

        // ── Command: Repeat ────────────────────────────────────────────────
        binding.btnRepeat.setOnClickListener {
            requireActiveSession {
                session.handleExternalCommand("repeat")
                pulse(binding.btnRepeat)
            }
        }

        // ── Command: Speak slower (cycles through 3 speeds) ───────────────
        binding.btnSlower.setOnClickListener {
            requireActiveSession {
                speechRateIndex = (speechRateIndex + 1) % speechRates.size
                val rate = speechRates[speechRateIndex]
                session.speechManager.setSpeechRate(rate)
                val label = when (speechRateIndex) {
                    0 -> "🐇 Vitesse normale"
                    1 -> "🐢 Lent"
                    else -> "🐌 Très lent"
                }
                binding.btnSlower.text = label
                session.handleExternalCommand("slower")
                pulse(binding.btnSlower)
            }
        }

        // ── Command: My level / progress ──────────────────────────────────
        binding.btnProgress.setOnClickListener {
            requireActiveSession {
                session.handleExternalCommand("stats")
                pulse(binding.btnProgress)
            }
        }

        // ── Command: Level down ────────────────────────────────────────────
        binding.btnLevelDown.setOnClickListener {
            requireActiveSession {
                session.levelDown()
                refreshLevelUI()
                pulse(binding.btnLevelDown)
            }
        }

        // ── Command: Level up ──────────────────────────────────────────────
        binding.btnLevelUp.setOnClickListener {
            requireActiveSession {
                session.levelUp()
                refreshLevelUI()
                pulse(binding.btnLevelUp)
            }
        }

        // ── Command: Pause / Resume ────────────────────────────────────────
        binding.btnPause.setOnClickListener {
            if (!isSessionActive) { toast("Démarrez d'abord une session."); return@setOnClickListener }
            if (!isPaused) {
                isPaused = true
                binding.btnPause.text = "▶ Reprendre"
                session.pauseSession()
            } else {
                isPaused = false
                binding.btnPause.text = "⏸ Pause"
                session.resumeSession()
            }
            pulse(binding.btnPause)
        }
    }

    // ── Session control ───────────────────────────────────────────────────────

    private fun doStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (!session.geminiManager.hasApiKey()) { showApiKeyDialog(); return }

        isSessionActive = true
        isPaused        = false
        speechRateIndex = 0
        binding.btnSlower.text = "🐢 Plus lent"

        binding.btnStartStop.text = "■  Arrêter"
        binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_light))

        stats.startTimer()
        session.startSession()
        refreshLevelUI()
    }

    private fun doStop() {
        isSessionActive = false
        isPaused        = false

        binding.btnStartStop.text = "▶  Démarrer la leçon"
        binding.btnStartStop.setBackgroundColor(
            resources.getColor(com.google.android.material.R.color.design_default_color_primary, theme)
        )
        binding.btnPause.text = "⏸ Pause"
        stopMicPulse()
        stats.stopTimer()
        session.stopSession()
        setStatus("Session terminée. Appuyez sur Démarrer pour recommencer.")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) doStart()
        else toast("Permission microphone requise pour les leçons vocales.")
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun refreshLevelUI() {
        val lm     = session.levelManager
        val level  = lm.currentLevel
        val prog   = (lm.progress() * 100).toInt().coerceIn(1, 100)

        binding.tvLevelBadge.text  = "${level.shortName}"
        binding.tvLevelNumber.text = "${level.number} / 100"
        binding.progressLevel.progress = prog

        // French badge: visible only for levels 1–5
        binding.tvFrenchBadge.visibility =
            if (level.useFrenchHelp) View.VISIBLE else View.GONE
    }

    private fun appendMessage(speaker: String, text: String, isEmma: Boolean) {
        val current   = binding.tvConversation.text.toString()
        val sep       = if (current.isNotEmpty()) "\n\n" else ""
        val header    = if (isEmma) "── Emma ──────────────────\n" else "── Vous ─────────────────\n"
        binding.tvConversation.text = "$current$sep$header$text"
        binding.scrollConversation.post { binding.scrollConversation.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setStatus(text: String) { binding.tvStatus.text = text }

    private fun startMicPulse() {
        if (micPulse?.isRunning == true) return
        binding.viewMicIndicator.visibility = View.VISIBLE
        micPulse = AnimatorInflater.loadAnimator(this, R.animator.mic_pulse) as? AnimatorSet
        micPulse?.setTarget(binding.viewMicIndicator)
        micPulse?.start()
    }

    private fun stopMicPulse() {
        micPulse?.cancel(); micPulse = null
        binding.viewMicIndicator.visibility = View.GONE
    }

    private fun pulse(view: View) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
            .start()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun requireActiveSession(block: () -> Unit) {
        if (!isSessionActive || isPaused) {
            toast("Démarrez ou reprenez la session d'abord.")
        } else {
            block()
        }
    }

    // ── API Key dialog ────────────────────────────────────────────────────────

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            hint    = "AIza…"
            setText(session.geminiManager.getApiKey())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("🔑  Clé API Gemini")
            .setMessage(
                "Collez votre clé API Google Gemini ci-dessous.\n\n" +
                "Obtenez une clé GRATUITE sur :\nhttps://aistudio.google.com\n\n" +
                "Votre clé est stockée uniquement sur cet appareil."
            )
            .setView(input)
            .setPositiveButton("Enregistrer") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    session.geminiManager.saveApiKey(key)
                    setStatus("Clé API enregistrée ✓  Prêt à apprendre !")
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
