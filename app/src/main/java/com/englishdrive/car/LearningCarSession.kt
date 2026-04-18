package com.englishdrive.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Android Auto Session – creates and returns the main learning screen.
 */
class LearningCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return LearningCarScreen(carContext)
    }
}
