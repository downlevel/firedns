package dev.downlevel.firedns.vpn

import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Simulates VPN start and stop for UI work. Used by previews and tests. */
class FakeVpnController(private val scope: CoroutineScope) : VpnController {

    private val _state = MutableStateFlow<VpnState>(VpnState.Off)
    override val state: StateFlow<VpnState> = _state.asStateFlow()

    private var startJob: Job? = null

    override fun start() {
        startJob?.cancel()
        _state.value = VpnState.Starting
        startJob = scope.launch {
            delay(START_DELAY_MS)
            _state.value = VpnState.Active
        }
    }

    override fun stop() {
        startJob?.cancel()
        _state.value = VpnState.Off
    }

    override fun permissionIntent(): Intent? = null

    override fun onPermissionDenied() {
        _state.value = VpnState.Error(VpnError.PERMISSION_DENIED)
    }

    companion object {
        const val START_DELAY_MS = 800L
    }
}
