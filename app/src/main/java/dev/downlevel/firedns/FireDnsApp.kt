package dev.downlevel.firedns

import android.app.Application
import dev.downlevel.firedns.update.UpdateScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FireDnsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Also covers installs that predate the updater: the job is created on the first launch.
        container.appScope.launch {
            if (BuildConfig.UPDATE_CHECK && container.settingsRepository.settings.first().checkUpdates) {
                UpdateScheduler.schedule(this@FireDnsApp)
            }
        }
    }
}
