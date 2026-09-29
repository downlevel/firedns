package dev.downlevel.firedns.vpn

import dev.downlevel.firedns.data.Settings

/** When to turn the VPN back on without the user opening the app. */
object Autostart {
    enum class Trigger {
        /** Fire TV boot. */
        BOOT,

        /** App update (the process and the VPN are killed). */
        APP_UPDATED
    }

    /**
     * Only if the user left it on. On boot it honors "Start on boot"; after an update
     * it always restores the previous state.
     */
    fun shouldStart(settings: Settings, trigger: Trigger): Boolean =
        settings.vpnDesiredOn && (trigger == Trigger.APP_UPDATED || settings.autostartOnBoot)
}
