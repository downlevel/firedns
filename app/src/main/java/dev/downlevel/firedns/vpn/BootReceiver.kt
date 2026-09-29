package dev.downlevel.firedns.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import dev.downlevel.firedns.FireDnsApp
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
            } catch (e: Exception) {
                Log.w("FireDNS", "Autostart failed ($trigger)", e)
            } finally {
                pending.finish()
            }
        }
    }
}
