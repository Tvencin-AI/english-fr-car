package com.englishdrive.learning

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks and persists per-session learning statistics.
 * Shown in the phone UI stats bar and available via the "my progress" voice command.
 */
class SessionStats(context: Context) {

    companion object {
        private const val PREFS = "english_drive_prefs"
        private const val KEY_TOTAL_MINUTES     = "total_minutes"
        private const val KEY_CORRECTIONS       = "total_corrections"
        private const val KEY_LAST_SESSION_DATE = "last_session_date"
        private const val KEY_STREAK            = "day_streak"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var totalMinutes: Int
        get()      = prefs.getInt(KEY_TOTAL_MINUTES, 0)
        private set(v) = prefs.edit().putInt(KEY_TOTAL_MINUTES, v).apply()

    var totalCorrections: Int
        get()      = prefs.getInt(KEY_CORRECTIONS, 0)
        private set(v) = prefs.edit().putInt(KEY_CORRECTIONS, v).apply()

    var dayStreak: Int
        get()      = prefs.getInt(KEY_STREAK, 0)
        private set(v) = prefs.edit().putInt(KEY_STREAK, v).apply()

    private var lastSessionDate: String
        get()      = prefs.getString(KEY_LAST_SESSION_DATE, "") ?: ""
        set(v)     = prefs.edit().putString(KEY_LAST_SESSION_DATE, v).apply()

    // Session timer
    private var sessionStartMs: Long = 0L

    fun startTimer() { sessionStartMs = System.currentTimeMillis() }

    fun stopTimer() {
        if (sessionStartMs == 0L) return
        val elapsedMinutes = ((System.currentTimeMillis() - sessionStartMs) / 60_000).toInt()
        totalMinutes += elapsedMinutes
        sessionStartMs = 0L
        updateStreak()
    }

    fun recordCorrection() { totalCorrections++ }

    private fun updateStreak() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
        if (lastSessionDate == today) return   // already counted today

        val yesterday = getYesterdayString()
        dayStreak = if (lastSessionDate == yesterday) dayStreak + 1 else 1
        lastSessionDate = today
    }

    private fun getYesterdayString(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(cal.time)
    }

    fun summaryText(): String = buildString {
        append("${totalMinutes} min studied  |  ")
        append("${totalCorrections} corrections  |  ")
        append("${dayStreak} day streak")
    }
}
