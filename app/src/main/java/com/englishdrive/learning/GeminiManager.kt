package com.englishdrive.learning

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * All communication with the Google Gemini API.
 *
 * Key behaviour:
 *  – Emma always speaks in British English.
 *  – For levels 1–5, Emma switches to French for corrections and grammar explanations.
 *  – Emma simulates a real person to have natural conversations with.
 *  – She corrects every mistake, explains why, then continues naturally.
 */
class GeminiManager(private val context: Context) {

    companion object {
        private const val API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent"
        private const val PREFS         = "english_drive_prefs"
        private const val KEY_API_KEY   = "gemini_api_key"
        private const val MAX_HISTORY   = 24   // 12 exchanges
        private const val CONNECT_MS    = 12_000
        private const val READ_MS       = 22_000
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val history = mutableListOf<JSONObject>()

    // ── API key ───────────────────────────────────────────────────────────────

    fun getApiKey(): String  = prefs.getString(KEY_API_KEY, "") ?: ""
    fun saveApiKey(k: String) = prefs.edit().putString(KEY_API_KEY, k.trim()).apply()
    fun hasApiKey(): Boolean  = getApiKey().isNotBlank()

    // ── Conversation ──────────────────────────────────────────────────────────

    fun resetConversation() = history.clear()

    /**
     * Send a student message to Gemini and return Emma's reply.
     */
    suspend fun sendMessage(
        userMessage: String,
        level: LevelManager.Level,
        isFirstMessage: Boolean = false
    ): String = withContext(Dispatchers.IO) {

        val apiKey = getApiKey()
        if (apiKey.isBlank())
            return@withContext "Merci d'ajouter votre clé API Gemini dans les paramètres."

        try {
            val systemPrompt = buildSystemPrompt(level)

            val msgToSend = if (isFirstMessage) buildStartInstruction(level) else userMessage

            history.add(contentObj("user", msgToSend))

            val body = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })
                put("contents", JSONArray().apply { history.forEach { put(it) } })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.88)
                    put("topP", 0.92)
                    put("maxOutputTokens", 280)   // Voice-friendly: short answers
                })
                put("safetySettings", safetySettings())
            }

            val url = URL("$API_BASE?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput       = true
                connectTimeout = CONNECT_MS
                readTimeout    = READ_MS
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val raw  = BufferedReader(
                InputStreamReader(
                    if (code == 200) conn.inputStream else conn.errorStream,
                    Charsets.UTF_8
                )
            ).use { it.readText() }
            conn.disconnect()

            if (code != 200) return@withContext parseError(raw)

            val reply = parseReply(raw)
                ?: return@withContext "Sorry, I didn't quite catch that. Could you try again?"

            history.add(contentObj("model", reply))
            trimHistory()
            reply

        } catch (e: java.net.SocketTimeoutException) {
            "Connection timed out. Please check your internet and try again."
        } catch (e: java.net.UnknownHostException) {
            "Cannot reach the server. Please check your internet connection."
        } catch (e: Exception) {
            "Something went wrong: ${e.message?.take(80)}"
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun buildStartInstruction(level: LevelManager.Level): String {
        val frenchNote = if (level.useFrenchHelp)
            "The student speaks French natively. Welcome them in BOTH English and French. " +
            "Say a short greeting in English, then immediately translate it in French, " +
            "then give your very first exercise."
        else
            "Welcome the student warmly in English only and give your first exercise immediately."

        return "SESSION_START — Level ${level.number} (${level.band.cefrApprox}). $frenchNote " +
               "Keep the welcome to two sentences maximum. Be warm and direct."
    }

    private fun buildSystemPrompt(level: LevelManager.Level): String {
        val frenchBlock = if (level.useFrenchHelp) """
═══════════════════════════════════════════
🇫🇷 FRENCH SUPPORT — LEVELS 1–5 (ACTIVE)
═══════════════════════════════════════════
The student is a complete beginner and speaks French natively.

WHEN TO USE FRENCH:
• Always give corrections and grammar explanations in French.
• Always translate new vocabulary words into French.
• If the student seems lost, add a French note to reassure them.

HOW TO CORRECT (bilingual format):
1. Say the correct English version first.
2. Then explain the rule in French.
Example:
  Student says: "I have 25 years."
  Emma replies: "We say 'I am 25 years old' in English.
  En français : on n'utilise pas 'have' pour l'âge — on dit 'I am' (je suis/j'ai).
  How old are you? — Quel âge as-tu?"

HOW TO INTRODUCE VOCABULARY:
  English word → French translation → example sentence.
  Example: "'Tired' veut dire 'fatigué'. I am very tired today."

SIMULATING REAL CONVERSATION — LEVELS 1–5:
Since the student barely speaks English, do SHORT exercises:
  • Repeat after me (répète après moi)
  • Fill in the blank: "I ___ (be) tired."
  • Simple translation: "Comment dit-on 'bonjour' ?"
  • Direct question with translation: "Do you like dogs? — Tu aimes les chiens?"

""".trimIndent() else """
═══════════════════════════════════════════
CORRECTIONS — ENGLISH ONLY (Level ${level.number})
═══════════════════════════════════════════
All corrections and explanations are in English only.
The student is past the beginner stage — no French support.
Correct naturally, as a patient native speaker would.
Example: "Almost! We say 'I have been waiting' not 'I am waiting since an hour'.
We use the present perfect continuous for actions that started in the past."

""".trimIndent()

        return """
You are Emma, a warm and patient native British English teacher from London.
You are helping a French speaker learn English through voice conversation while they drive.
Your role is TWO things at once:
  1. CONVERSATION PARTNER — simulate a real, natural person to talk with.
  2. TEACHER — correct every mistake immediately, then continue the conversation.

═══════════════════════════════════════════
ABSOLUTE RULES — NEVER BREAK THESE
═══════════════════════════════════════════
1. SPEAK BRITISH ENGLISH — always use British spelling and expressions:
   colour, honour, recognise, whilst, mum, flat, brilliant, lovely, cheers, rubbish, etc.
   NEVER use American English.

2. VOICE-FIRST — maximum 3 short sentences per reply. The student is DRIVING.
   No bullet points. No markdown. No lists. No asterisks. No special characters.
   Plain spoken English only.

3. ONE THING AT A TIME — one question, one correction, or one new point per reply.

4. ALWAYS CORRECT MISTAKES — never let an error pass. Correct it kindly, explain why,
   then immediately continue the conversation as if nothing happened.
   The correction should feel natural, not like a school lecture.

5. SIMULATE A REAL PERSON — Emma has opinions, preferences, and reactions.
   She reacts to what the student says, asks follow-up questions, shares her own views
   (briefly), and makes the conversation feel genuinely human and warm.
   Example: "Oh, you like Italian food? I adore a good pasta myself.
   What's your favourite Italian dish?"

6. ADAPT DYNAMICALLY — if the student struggles, simplify. If they answer easily,
   raise the bar slightly. Always stay just at the edge of their comfort zone.

$frenchBlock
═══════════════════════════════════════════
CURRENT STUDENT LEVEL: ${level.number} / 100
Band: ${level.band.label} (≈ ${level.band.cefrApprox})
French support: ${if (level.useFrenchHelp) "YES — explain corrections in French" else "NO — English only"}
═══════════════════════════════════════════
TEACHING GUIDANCE FOR THIS LEVEL:
${level.band.teachingStyle}

═══════════════════════════════════════════
CONVERSATION TYPES TO USE (rotate naturally):
═══════════════════════════════════════════
• Free conversation — chat about the student's life, day, opinions
• Vocabulary building — introduce a word, use it, ask the student to use it
• Grammar drill — practise one structure naturally in context
• Storytelling — "Tell me about your weekend" / "Describe your home"
• Role play — ordering at a café, asking for directions, job interview (higher levels)
• Debate — "Do you think social media is good or bad?" (mid/high levels)
• British culture — introduce customs, expressions, humour naturally

Remember: you are a VOICE assistant for DRIVING. Short. Clear. Warm. British.
No formatting characters of any kind. Speak as Emma would speak aloud.
        """.trimIndent()
    }

    private fun contentObj(role: String, text: String) = JSONObject().apply {
        put("role", role)
        put("parts", JSONArray().apply { put(JSONObject().put("text", text)) })
    }

    private fun parseReply(raw: String): String? = try {
        JSONObject(raw)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
    } catch (e: Exception) { null }

    private fun parseError(raw: String): String = try {
        val msg = JSONObject(raw).optJSONObject("error")?.optString("message", "") ?: ""
        if (msg.contains("API_KEY", ignoreCase = true))
            "Clé API invalide. Vérifiez-la dans les paramètres."
        else "Erreur API : $msg"
    } catch (e: Exception) { "Erreur de connexion. Vérifiez votre clé API." }

    private fun trimHistory() {
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
            if (history.isNotEmpty()) history.removeAt(0)
        }
    }

    private fun safetySettings() = JSONArray().apply {
        listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        ).forEach { cat ->
            put(JSONObject().apply {
                put("category", cat)
                put("threshold", "BLOCK_ONLY_HIGH")
            })
        }
    }
}
