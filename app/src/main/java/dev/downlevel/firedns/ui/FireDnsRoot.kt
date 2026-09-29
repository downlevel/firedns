package dev.downlevel.firedns.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import dev.downlevel.firedns.AppContainer
import dev.downlevel.firedns.BuildConfig
import dev.downlevel.firedns.ui.editor.EditorForm
import dev.downlevel.firedns.ui.editor.EditorScreen
import dev.downlevel.firedns.ui.home.HomeScreen
import dev.downlevel.firedns.ui.home.HomeViewModel
import dev.downlevel.firedns.ui.navigation.Navigator
import dev.downlevel.firedns.ui.navigation.Screen
import dev.downlevel.firedns.ui.onboarding.OnboardingScreen
import dev.downlevel.firedns.ui.settings.InfoScreen
import dev.downlevel.firedns.ui.settings.SettingsScreen
import dev.downlevel.firedns.vpn.isOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI root: picks the first screen (onboarding only on first launch) and wires the
 * screens to the repositories. Writes use the appScope, so they are not cancelled
 * when the user leaves the screen.
 */
@Composable
fun FireDnsRoot(container: AppContainer) {
    val onboardingDone by produceState<Boolean?>(initialValue = null) {
        value = container.settingsRepository.settings.first().onboardingDone
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val done = onboardingDone ?: return@Box
        val navigator = remember { Navigator(if (done) Screen.Home else Screen.Onboarding) }
        val startVpn = rememberVpnStarter(container)
        // On the Home screen Back closes the app; the VPN stays on.
        BackHandler(enabled = navigator.canGoBack) { navigator.pop() }

        when (val screen = navigator.current) {
            Screen.Onboarding -> OnboardingScreen(
                onContinue = {
                    container.appScope.launch { container.settingsRepository.setOnboardingDone() }
                    navigator.replaceAll(Screen.Home)
                    startVpn()
                }
            )
            Screen.Home -> HomeRoute(container, navigator, startVpn)
            is Screen.Editor -> EditorRoute(container, screen.profileId, onDone = navigator::pop)
            Screen.Settings -> SettingsRoute(container, onOpenInfo = { navigator.push(Screen.Info) })
            Screen.Info -> InfoScreen(versionName = BuildConfig.VERSION_NAME)
        }
    }
}

/**
 * Starts the VPN, showing Android's consent dialog first when needed.
 * If consent is denied, the Home screen shows the error with "Retry".
 */
@Composable
private fun rememberVpnStarter(container: AppContainer): () -> Unit {
    val controller = container.vpnController
    val start = {
        controller.start()
        container.appScope.launch { container.settingsRepository.setVpnDesiredOn(true) }
        Unit
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) start() else controller.onPermissionDenied()
    }
    return remember(consent) {
        { controller.permissionIntent()?.let(consent::launch) ?: start() }
    }
}

@Composable
private fun HomeRoute(container: AppContainer, navigator: Navigator, startVpn: () -> Unit) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onToggle = { if (state.vpnState.isOn) viewModel.stopVpn() else startVpn() },
        onRetry = startVpn,
        onSelect = viewModel::select,
        onAdd = { navigator.push(Screen.Editor(profileId = null)) },
        onEdit = { navigator.push(Screen.Editor(profileId = it.id)) },
        onDelete = viewModel::delete,
        onOpenSettings = { navigator.push(Screen.Settings) }
    )
}

@Composable
private fun EditorRoute(container: AppContainer, profileId: String?, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(profileId == null) }
    LaunchedEffect(profileId) {
        if (!loaded && profileId != null) {
            container.profileRepository.profiles.first().firstOrNull { it.id == profileId }?.let {
                name = it.name
                address = it.address
            }
            loaded = true
        }
    }
    if (!loaded) return

    val form = EditorForm(name, address)
    EditorScreen(
        isNew = profileId == null,
        form = form,
        onNameChange = { name = it.take(EditorForm.MAX_NAME_LENGTH) },
        onAddressChange = { address = it },
        onSave = {
            form.toProfile(profileId)?.let { profile ->
                container.appScope.launch { container.profileRepository.saveCustom(profile) }
                onDone()
            }
        },
        onCancel = onDone
    )
}

@Composable
private fun SettingsRoute(container: AppContainer, onOpenInfo: () -> Unit) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings ?: return
    val repository = container.settingsRepository
    SettingsScreen(
        settings = current,
        onAutostartChange = { container.appScope.launch { repository.setAutostartOnBoot(it) } },
        onFallbackChange = { container.appScope.launch { repository.setFallbackEnabled(it) } },
        onOpenInfo = onOpenInfo
    )
}
