package dev.downlevel.firedns.vpn

import dev.downlevel.firedns.data.Settings
import dev.downlevel.firedns.vpn.Autostart.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutostartTest {

    @Test
    fun `on boot only if it was on and the setting is enabled`() {
        assertTrue(Autostart.shouldStart(Settings(vpnDesiredOn = true, autostartOnBoot = true), Trigger.BOOT))
        assertFalse(Autostart.shouldStart(Settings(vpnDesiredOn = true, autostartOnBoot = false), Trigger.BOOT))
        assertFalse(Autostart.shouldStart(Settings(vpnDesiredOn = false, autostartOnBoot = true), Trigger.BOOT))
    }

    @Test
    fun `after an update restores the previous state`() {
        assertTrue(Autostart.shouldStart(Settings(vpnDesiredOn = true, autostartOnBoot = false), Trigger.APP_UPDATED))
        assertFalse(Autostart.shouldStart(Settings(vpnDesiredOn = false), Trigger.APP_UPDATED))
    }
}
