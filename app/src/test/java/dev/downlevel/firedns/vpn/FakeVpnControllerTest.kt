package dev.downlevel.firedns.vpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeVpnControllerTest {

    @Test
    fun `start, activation and stop`() = runTest {
        val vpn = FakeVpnController(backgroundScope)
        assertEquals(VpnState.Off, vpn.state.value)
        assertFalse(vpn.state.value.isOn)

        vpn.start()
        assertEquals(VpnState.Starting, vpn.state.value)
        assertTrue(vpn.state.value.isOn)

        advanceTimeBy(FakeVpnController.START_DELAY_MS)
        runCurrent()
        assertEquals(VpnState.Active, vpn.state.value)

        vpn.stop()
        assertEquals(VpnState.Off, vpn.state.value)
    }

    @Test
    fun `stop while starting cancels activation`() = runTest {
        val vpn = FakeVpnController(backgroundScope)
        vpn.start()
        vpn.stop()
        advanceTimeBy(FakeVpnController.START_DELAY_MS * 2)
        runCurrent()
        assertEquals(VpnState.Off, vpn.state.value)
    }

    @Test
    fun `permission denied`() = runTest {
        val vpn = FakeVpnController(backgroundScope)
        assertEquals(null, vpn.permissionIntent())
        vpn.onPermissionDenied()
        assertEquals(VpnState.Error(VpnError.PERMISSION_DENIED), vpn.state.value)
        assertFalse(vpn.state.value.isOn)
    }
}
