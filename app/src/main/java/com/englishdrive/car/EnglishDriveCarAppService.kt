package com.englishdrive.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for Android Auto.
 * Registered in the manifest as a CarAppService.
 */
class EnglishDriveCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Allow all hosts during development.
        // For production, use HostValidator.ALLOW_DEBUG_HOSTS or a proper allowlist.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return LearningCarSession()
    }
}
