package com.englishdrive

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages first-launch onboarding state.
 * Tracks whether the user has seen the welcome screen and set up their API key.
 */
object OnboardingManager {

    private const val PREFS      = "english_drive_prefs"
    private const val KEY_ONBOARDED = "onboarding_complete"

    fun isOnboarded(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ONBOARDED, false)
    }

    fun markOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
