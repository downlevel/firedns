package dev.downlevel.firedns.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import kotlinx.coroutines.flow.StateFlow

/** Real controller: starts and stops [DnsVpnService]; the service updates the state in [store]. */
class AndroidVpnController(private val context: Context, private val store: VpnStateStore) : VpnController {

    override val state: StateFlow<VpnState> = store.state

    override fun permissionIntent(): Intent? = VpnService.prepare(context)

    override fun start() {
        store.set(VpnState.Starting)
        context.startForegroundService(DnsVpnService.startIntent(context))
    }

    override fun stop() {
        if (state.value is VpnState.Error) {
            store.set(VpnState.Off)
            return
        }
        context.startService(DnsVpnService.stopIntent(context))
    }

    override fun onPermissionDenied() {
        store.set(VpnState.Error(VpnError.PERMISSION_DENIED))
    }
}
