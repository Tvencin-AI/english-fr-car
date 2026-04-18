package com.englishdrive.learning

import android.content.Context
import android.content.SharedPreferences

/**
 * 100-level English progression system.
 *
 * Band structure:
 *   Levels  1– 5  : Absolute beginner – Emma explains in French
 *   Levels  6–15  : A1  – Very basic English, French allowed for corrections
 *   Levels 16–25  : A1+ – Foundations solidifying
 *   Levels 26–35  : A2  – Elementary everyday English
 *   Levels 36–45  : A2+ – Growing confidence
 *   Levels 46–55  : B1  – Pre-intermediate conversational
 *   Levels 56–65  : B1+ – Intermediate, phrasal verbs, opinions
 *   Levels 66–75  : B2  – Upper-intermediate, fluency focus
 *   Levels 76–85  : C1  – Advanced, nuance, idioms
 *   Levels 86–95  : C1+ – Near-native sophistication
 *   Levels 96–100 : C2  – Native-level fluency
 */
class LevelManager(context: Context) {

    companion object {
        private const val PREFS             = "english_drive_prefs"
        private const val KEY_LEVEL         = "current_level_v2"
        private const val KEY_SESSION_COUNT = "session_count"
        private const val KEY_EXCHANGES     = "total_exchanges"
        const val MAX_LEVEL = 100
        const val MIN_LEVEL = 1
    }

    // ── Level data class ──────────────────────────────────────────────────────

    data class Level(
        val number: Int,           // 1–100
        val band: Band,            // CEFR-like band
        val shortName: String,     // e.g. "Débutant absolu"
        val useFrenchHelp: Boolean // true → Emma may explain in French
    )

    enum class Band(
        val label: String,
        val cefrApprox: String,
        val teachingStyle: String
    ) {
        ABSOLUTE_BEGINNER(
            "Débutant absolu",
            "Pre-A1",
            """
Use ONLY the simplest English words. Sentences of 2–4 words maximum.
Topics: hello/goodbye, please/thank you, numbers 1–10, yes/no.
Speak very slowly and clearly.
IMPORTANT: When correcting a mistake or explaining grammar, switch to French.
Say the English first, then explain in French.
Example: "We say 'I am', not 'I is'. En français : avec 'I', on utilise toujours 'am'."
The student understands almost no English. Be extremely patient and encouraging.
Start every session with: "Hello! I'm Emma, your English teacher. Bonjour! Je suis Emma."
            """.trimIndent()
        ),
        A1(
            "Grand débutant",
            "A1",
            """
Use very simple present tense sentences. Max 5–6 words per sentence.
Topics: colours, animals, body parts, family members, basic food, days of week.
For corrections and grammar explanations: use French to clarify.
Example correction: "We say 'She has' not 'She have'. Le verbe 'have' prend un 's' avec she/he/it."
Introduce one new vocabulary word per exchange. Always use it in a sentence.
            """.trimIndent()
        ),
        A1_PLUS(
            "Débutant en progression",
            "A1+",
            """
Simple present and 'going to' future. Short sentences. 6–8 words.
Topics: daily routine, home, simple directions, weather, shopping basics.
Correct in English first, brief French note only for complex grammar points.
Ask simple questions: "What do you do every morning?"
Introduce common contractions (I'm, it's, don't).
            """.trimIndent()
        ),
        A2(
            "Élémentaire",
            "A2",
            """
Present simple, present continuous, simple past. Up to 10 words per sentence.
Topics: travel, food ordering, describing people, hobbies, feelings.
Correct in English with a brief French note only when truly needed.
Encourage full sentences. Gently prompt if answer is too short.
Introduce common question forms: How long? How often? What kind of?
            """.trimIndent()
        ),
        A2_PLUS(
            "Élémentaire avancé",
            "A2+",
            """
All basic tenses plus 'used to'. Natural sentence length.
Topics: experiences, comparisons, plans, simple stories, British daily life.
Correct primarily in English. French only for genuinely tricky grammar.
Introduce 2–3 new vocabulary items per session naturally in context.
Encourage longer answers. Ask "Why?" and "What happened next?"
            """.trimIndent()
        ),
        B1(
            "Pré-intermédiaire",
            "B1",
            """
Present perfect, conditionals (1st), reported speech basics.
Topics: opinions, current events (simple), work, travel anecdotes.
All corrections and explanations in English only.
Introduce phrasal verbs: explain them naturally. e.g. "To give up means to stop trying."
Encourage the student to tell short stories or describe recent events.
            """.trimIndent()
        ),
        B1_PLUS(
            "Intermédiaire",
            "B1+",
            """
All tenses. 2nd conditional. Passive voice. Modal verbs fully.
Topics: debating preferences, hypotheticals, British culture, news.
Introduce common idioms with natural explanation: "Under the weather means feeling ill."
Challenge with open questions that require 2–3 sentence answers.
Correct grammar and vocabulary with full English explanations.
            """.trimIndent()
        ),
        B2(
            "Intermédiaire supérieur",
            "B2",
            """
Complex grammar, 3rd conditional, mixed conditionals, subjunctive.
Topics: complex opinions, culture, British humour, abstract concepts.
Introduce advanced phrasal verbs, collocations, formal vs informal register.
Push the student to use more varied vocabulary. If they use a basic word,
suggest a more sophisticated alternative: "Good word — you could also say 'delightful'."
Have real conversations. Challenge directly.
            """.trimIndent()
        ),
        C1(
            "Avancé",
            "C1",
            """
Near-native grammar. Focus on precision, nuance, register, idioms.
Topics: sophisticated debate, literature references, British wit and understatement.
Correct only the most subtle errors. Explain nuance in word choice.
e.g. "Both work, but 'furious' implies something more intense than 'angry' here."
Speak at a fully natural British pace. Use British expressions freely.
            """.trimIndent()
        ),
        C1_PLUS(
            "Très avancé",
            "C1+",
            """
Sophisticated English. Complex syntax, rare vocabulary, cultural depth.
Topics: philosophy, history, British literature, dry humour, wordplay.
Treat the student as a near-peer. Have genuine intellectual discussions.
Point out extremely subtle nuances: register, connotation, stylistic choices.
Only interrupt for errors that a native speaker would notice.
            """.trimIndent()
        ),
        C2(
            "Niveau natif",
            "C2",
            """
Full native-level fluency. Engage completely as with a native speaker.
Topics: anything and everything — literature, culture, abstract ideas, humour.
Correct only the rarest errors. Discuss at full natural speed and complexity.
Use British cultural references, wit, sarcasm naturally.
The student is a peer. Have the most interesting conversation possible.
            """.trimIndent()
        );
    }

    // ── Level generation ──────────────────────────────────────────────────────

    /**
     * Returns the [Band] and French-help flag for a given level number (1–100).
     */
    fun levelData(number: Int): Level {
        val n = number.coerceIn(MIN_LEVEL, MAX_LEVEL)
        val band = when (n) {
            in  1.. 5  -> Band.ABSOLUTE_BEGINNER
            in  6..15  -> Band.A1
            in 16..25  -> Band.A1_PLUS
            in 26..35  -> Band.A2
            in 36..45  -> Band.A2_PLUS
            in 46..55  -> Band.B1
            in 56..65  -> Band.B1_PLUS
            in 66..75  -> Band.B2
            in 76..85  -> Band.C1
            in 86..95  -> Band.C1_PLUS
            else       -> Band.C2       // 96–100
        }
        val useFrench = n <= 5
        val shortName = when (n) {
            1    -> "Débutant absolu"
            in 2.. 5  -> "Débutant (niveau $n)"
            in 6..15  -> "Grand débutant (niv. $n)"
            in 16..25 -> "Débutant avancé (niv. $n)"
            in 26..35 -> "Élémentaire (niv. $n)"
            in 36..45 -> "Élémentaire+ (niv. $n)"
            in 46..55 -> "Pré-intermédiaire (niv. $n)"
            in 56..65 -> "Intermédiaire (niv. $n)"
            in 66..75 -> "Intermédiaire+ (niv. $n)"
            in 76..85 -> "Avancé (niv. $n)"
            in 86..95 -> "Très avancé (niv. $n)"
            else      -> "Niveau natif (niv. $n)"
        }
        return Level(number = n, band = band, shortName = shortName, useFrenchHelp = useFrench)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var currentLevelNumber: Int
        get()      = prefs.getInt(KEY_LEVEL, 1).coerceIn(MIN_LEVEL, MAX_LEVEL)
        set(value) = prefs.edit().putInt(KEY_LEVEL, value.coerceIn(MIN_LEVEL, MAX_LEVEL)).apply()

    val currentLevel: Level get() = levelData(currentLevelNumber)

    var sessionCount: Int
        get()      = prefs.getInt(KEY_SESSION_COUNT, 0)
        private set(v) = prefs.edit().putInt(KEY_SESSION_COUNT, v).apply()

    var totalExchanges: Int
        get()      = prefs.getInt(KEY_EXCHANGES, 0)
        private set(v) = prefs.edit().putInt(KEY_EXCHANGES, v).apply()

    // ── Navigation ────────────────────────────────────────────────────────────

    fun incrementSession()  { sessionCount++ }
    fun incrementExchange() { totalExchanges++ }

    /** Move up by [steps] levels (default 1). Returns true if changed. */
    fun levelUp(steps: Int = 1): Boolean {
        val next = (currentLevelNumber + steps).coerceAtMost(MAX_LEVEL)
        return if (next != currentLevelNumber) {
            currentLevelNumber = next; true
        } else false
    }

    /** Move down by [steps] levels (default 1). Returns true if changed. */
    fun levelDown(steps: Int = 1): Boolean {
        val prev = (currentLevelNumber - steps).coerceAtLeast(MIN_LEVEL)
        return if (prev != currentLevelNumber) {
            currentLevelNumber = prev; true
        } else false
    }

    fun setLevel(n: Int) { currentLevelNumber = n }

    // ── Display helpers ───────────────────────────────────────────────────────

    /** Progress bar value 0.0–1.0 */
    fun progress(): Float = (currentLevelNumber - 1).toFloat() / (MAX_LEVEL - 1).toFloat()

    /** Short label: "Niveau 12 · A1" */
    fun badgeText(): String =
        "Niveau ${currentLevelNumber}  ·  ${currentLevel.band.cefrApprox}"
}
