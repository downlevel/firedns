package dev.downlevel.firedns

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dev.downlevel.firedns.data.ProfileRepository
import dev.downlevel.firedns.data.Settings
import dev.downlevel.firedns.data.SettingsRepository
import dev.downlevel.firedns.data.SettingsSerializer
import dev.downlevel.firedns.vpn.AndroidVpnController
import dev.downlevel.firedns.vpn.VpnController
import dev.downlevel.firedns.vpn.VpnStateStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Manual dependency injection: a single instance per process, created by [FireDnsApp]. */
class AppContainer(context: Context) {

    /** Process-wide scope for writes that must not depend on the open screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val settingsStore: DataStore<Settings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { Settings() },
        produceFile = { context.applicationContext.dataStoreFile(SETTINGS_FILE) }
    )

    val settingsRepository = SettingsRepository(settingsStore)
    val profileRepository = ProfileRepository(settingsStore)

    val vpnStateStore = VpnStateStore()
    val vpnController: VpnController = AndroidVpnController(context.applicationContext, vpnStateStore)

    /** Shared DoH client: HTTP/2 connections are reused across queries. */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private companion object {
        const val SETTINGS_FILE = "firedns_settings.json"
    }
}
