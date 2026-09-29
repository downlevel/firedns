package dev.downlevel.firedns.vpn

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnState {
    data object Off : VpnState

    data object Starting : VpnState

    data object Active : VpnState

    /** The chosen DNS does not answer: queries go to the network DNS. */
    data object Fallback : VpnState

    data class Error(val reason: VpnError) : VpnState
}

enum class VpnError {
    /** The user declined the VPN consent dialog. */
    PERMISSION_DENIED,

    /** Another VPN took the slot (onRevoke). */
    REVOKED
}

/** True when the VPN is on or starting: pressing the toggle turns it off. */
val VpnState.isOn: Boolean
    get() = this is VpnState.Starting || this is VpnState.Active || this is VpnState.Fallback

interface VpnController {
    val state: StateFlow<VpnState>

    /** VPN consent dialog intent to launch before [start], `null` if already granted. */
    fun permissionIntent(): Intent?

    fun start()

    fun stop()

    fun onPermissionDenied()
}

/** VPN state shared between the controller (UI) and the service. */
class VpnStateStore {
    private val _state = MutableStateFlow<VpnState>(VpnState.Off)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    fun set(state: VpnState) {
        _state.value = state
    }
}
