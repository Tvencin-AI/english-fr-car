package com.englishdrive.learning

/**
 * Parses spoken commands before sending to Gemini.
 * Handles level changes, session control, and utility commands.
 * Compatible with the 100-level system.
 */
object VoiceCommandParser {

    sealed class Command {
        data class SetLevel(val levelNumber: Int) : Command()
        object LevelUp        : Command()
        object LevelDown      : Command()
        object StopSession    : Command()
        object PauseSession   : Command()
        object ResumeSession  : Command()
        object RepeatLast     : Command()
        object AskStats       : Command()
        object SpeakSlower    : Command()
        object None           : Command()
    }

    // ── Pattern tables ────────────────────────────────────────────────────────

    private val stopWords    = listOf("stop", "end lesson", "finish lesson", "end session",
        "stop lesson", "goodbye emma", "bye emma", "that's enough", "that's all", "arrêter")

    private val pauseWords   = listOf("pause", "wait", "hold on", "one moment",
        "pause please", "wait please")

    private val resumeWords  = listOf("resume", "continue", "carry on", "let's continue",
        "let's carry on", "i'm ready", "reprendre", "ready")

    private val levelUpWords = listOf("level up", "go up", "harder", "more difficult",
        "too easy", "it's too easy", "next level", "promote me", "upgrade",
        "higher level", "move up")

    private val levelDownWords = listOf("level down", "go down", "easier", "too hard",
        "it's too hard", "too difficult", "previous level", "go back",
        "lower level", "move down", "c'est trop difficile")

    private val repeatWords  = listOf("repeat", "say again", "pardon", "sorry",
        "what did you say", "i didn't understand", "can you repeat",
        "please repeat", "again please", "repeat that", "one more time",
        "je n'ai pas compris")

    private val statsWords   = listOf("my level", "what level", "what level am i",
        "my progress", "how am i doing", "where am i",
        "tell me my level", "quel niveau")

    private val slowerWords  = listOf("speak slower", "slow down", "too fast",
        "slower please", "speak more slowly", "more slowly",
        "parle plus lentement")

    // Named level aliases (level name → number)
    private val levelAliases: Map<String, Int> = buildMap {
        // CEFR names
        put("beginner", 1);       put("débutant", 1)
        put("absolute beginner", 1)
        put("a one", 6);          put("a 1", 6)
        put("a two", 26);         put("a 2", 26)
        put("b one", 46);         put("b 1", 46)
        put("b two", 66);         put("b 2", 66)
        put("c one", 76);         put("c 1", 76)
        put("c two", 96);         put("c 2", 96)
        put("elementary", 26)
        put("pre intermediate", 46); put("pre-intermediate", 46)
        put("intermediate", 56)
        put("upper intermediate", 66); put("upper-intermediate", 66)
        put("advanced", 76)
        put("mastery", 96);       put("native", 100); put("fluent", 96)
        // French
        put("élémentaire", 26);   put("intermédiaire", 56)
        put("avancé", 76);        put("maîtrise", 96)
    }

    private val changeTriggers = listOf(
        "change level", "switch level", "change to", "switch to",
        "set level to", "set level", "go to level", "niveau"
    )

    // ── Public ────────────────────────────────────────────────────────────────

    fun parse(text: String): Command {
        val t = text.lowercase().trim().trimEnd('.', '!', '?')

        if (stopWords.any   { t == it || t.contains(it) }) return Command.StopSession
        if (pauseWords.any  { t == it || t.startsWith(it) }) return Command.PauseSession
        if (resumeWords.any { t == it || t.contains(it) }) return Command.ResumeSession
        if (levelUpWords.any   { t == it || t.contains(it) }) return Command.LevelUp
        if (levelDownWords.any { t == it || t.contains(it) }) return Command.LevelDown
        if (repeatWords.any { t == it || t.startsWith(it) }) return Command.RepeatLast
        if (statsWords.any  { t == it || t.contains(it) }) return Command.AskStats
        if (slowerWords.any { t == it || t.contains(it) }) return Command.SpeakSlower

        // "go to level 42" / "niveau 7" / "level 42"
        val numMatch = Regex("""(?:level|niveau)\s+(\d{1,3})""").find(t)
        if (numMatch != null) {
            val n = numMatch.groupValues[1].toIntOrNull()
            if (n != null && n in 1..100) return Command.SetLevel(n)
        }

        // Check if trigger + known alias
        val hasTrigger = changeTriggers.any { t.contains(it) }
        if (hasTrigger) {
            val lvl = extractNamedLevel(t)
            if (lvl != null) return Command.SetLevel(lvl)
        }

        // Direct alias e.g. student just says "advanced"
        val direct = levelAliases[t]
        if (direct != null) return Command.SetLevel(direct)

        // Ends with an alias
        for ((alias, lvl) in levelAliases) {
            if (t.endsWith(alias)) return Command.SetLevel(lvl)
        }

        return Command.None
    }

    private fun extractNamedLevel(text: String): Int? {
        for ((alias, lvl) in levelAliases) {
            if (text.contains(alias)) return lvl
        }
        return null
    }
}
