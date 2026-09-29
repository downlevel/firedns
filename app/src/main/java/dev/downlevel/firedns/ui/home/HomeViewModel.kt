package dev.downlevel.firedns.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.downlevel.firedns.AppContainer
import dev.downlevel.firedns.data.DnsProfile
import dev.downlevel.firedns.data.Presets
import dev.downlevel.firedns.data.ProfileRepository
import dev.downlevel.firedns.data.SettingsRepository
import dev.downlevel.firedns.vpn.VpnController
import dev.downlevel.firedns.vpn.VpnState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val vpnState: VpnState = VpnState.Off,
    val profiles: List<DnsProfile> = Presets.all,
    val activeProfile: DnsProfile = Presets.default
) {
    val hasCustomProfiles: Boolean get() = profiles.any { !it.isPreset }
}

class HomeViewModel(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val vpn: VpnController
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(profiles.profiles, profiles.activeProfile, vpn.state) { list, active, vpnState ->
            HomeUiState(vpnState = vpnState, profiles = list, activeProfile = active)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Starting requires VPN consent, handled in the UI (see FireDnsRoot). */
    fun stopVpn() {
        vpn.stop()
        viewModelScope.launch { settings.setVpnDesiredOn(false) }
    }

    fun select(profile: DnsProfile) {
        viewModelScope.launch { profiles.setActive(profile.id) }
    }

    fun delete(profile: DnsProfile) {
        viewModelScope.launch { profiles.deleteCustom(profile.id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(container.profileRepository, container.settingsRepository, container.vpnController)
            }
        }
    }
}
