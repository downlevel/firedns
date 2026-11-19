package dev.downlevel.firedns.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import dev.downlevel.firedns.BuildConfig
import dev.downlevel.firedns.FireDnsApp
import dev.downlevel.firedns.update.ReleaseNotes
import dev.downlevel.firedns.update.UpdateNotifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Turns the VPN back on at boot and after an app update (see [Autostart]). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> Autostart.Trigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> Autostart.Trigger.APP_UPDATED
            else -> return
        }
        val container = (context.applicationContext as FireDnsApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val settings = container.settingsRepository.settings.first()
                // Without VPN consent (revoked) it cannot start: the UI reports it on the next launch.
                if (Autostart.shouldStart(settings, trigger) && VpnService.prepare(context) == null) {
                    container.vpnController.start()
                }
                // Users rarely open the app: tell them the update landed and what it brings.
                val unseen = ReleaseNotes.shouldShowWhatsNew(
                    settings.onboardingDone,
                    settings.lastSeenVersionCode,
                    BuildConfig.VERSION_CODE
                )
                val hasNotes = container.updateManager.installedNotes().isNotEmpty()
                if (trigger == Autostart.Trigger.APP_UPDATED && unseen && hasNotes) {
                    UpdateNotifications.showUpdated(context, BuildConfig.VERSION_NAME)
                }
            } catch (e: Exception) {
                Log.w("FireDNS", "Autostart failed ($trigger)", e)
            } finally {
                pending.finish()
            }
        }
    }
}
