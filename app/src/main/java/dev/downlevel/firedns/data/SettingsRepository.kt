package dev.downlevel.firedns.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val store: DataStore<Settings>) {

    val settings: Flow<Settings> = store.data

    suspend fun setVpnDesiredOn(on: Boolean) = update { it.copy(vpnDesiredOn = on) }

    suspend fun setAutostartOnBoot(enabled: Boolean) = update { it.copy(autostartOnBoot = enabled) }

    suspend fun setFallbackEnabled(enabled: Boolean) = update { it.copy(fallbackEnabled = enabled) }

    suspend fun setOnboardingDone() = update { it.copy(onboardingDone = true) }

    private suspend fun update(transform: (Settings) -> Settings) {
        store.updateData(transform)
    }
}
