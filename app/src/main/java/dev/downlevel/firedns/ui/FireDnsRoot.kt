package dev.downlevel.firedns.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
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
import dev.downlevel.firedns.ui.update.InstallPermissionDialog
import dev.downlevel.firedns.ui.update.UpdateScreen
import dev.downlevel.firedns.ui.update.WhatsNewScreen
import dev.downlevel.firedns.update.ReleaseNotes
import dev.downlevel.firedns.update.UpdateState
import dev.downlevel.firedns.update.manifest
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
    val launch by produceState<LaunchInfo?>(initialValue = null) {
        val settings = container.settingsRepository.settings.first()
        val updates = container.updateManager
        val notes = updates.installedNotes()
        val showWhatsNew = notes.isNotEmpty() &&
            ReleaseNotes.shouldShowWhatsNew(
                settings.onboardingDone,
                settings.lastSeenVersionCode,
                BuildConfig.VERSION_CODE
            )
        // Recorded right away: leaving "What's new" with Back must not show it again on every launch.
        updates.markInstalledVersionSeen()
        updates.cleanUp()
        value = LaunchInfo(settings.onboardingDone, if (showWhatsNew) notes else emptyList())
        updates.check(notify = false)
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val info = launch ?: return@Box
        val navigator = remember {
            Navigator(if (info.onboardingDone) Screen.Home else Screen.Onboarding).apply {
                if (info.whatsNew.isNotEmpty()) push(Screen.WhatsNew)
            }
        }
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
            Screen.Update -> UpdateRoute(container, onLater = navigator::pop)
            Screen.WhatsNew -> WhatsNewScreen(
                versionName = BuildConfig.VERSION_NAME,
                notes = info.whatsNew,
                onDone = navigator::pop
            )
        }
    }
}

private data class LaunchInfo(val onboardingDone: Boolean, val whatsNew: List<String>)

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
    val update by container.updateManager.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onToggle = { if (state.vpnState.isOn) viewModel.stopVpn() else startVpn() },
        onRetry = startVpn,
        onSelect = viewModel::select,
        onAdd = { navigator.push(Screen.Editor(profileId = null)) },
        onEdit = { navigator.push(Screen.Editor(profileId = it.id)) },
        onDelete = viewModel::delete,
        onOpenSettings = { navigator.push(Screen.Settings) },
        updateVersionName = update.manifest?.versionName,
        onOpenUpdate = { navigator.push(Screen.Update) }
    )
}

@Composable
private fun UpdateRoute(container: AppContainer, onLater: () -> Unit) {
    val context = LocalContext.current
    val updates = container.updateManager
    val state by updates.state.collectAsStateWithLifecycle()
    var askPermission by remember { mutableStateOf(false) }
    val launchInstaller = {
        val intent = updates.installIntent()
        if (intent != null) context.startSafely(intent)
    }
    // Nothing (left) to update, e.g. the check was turned off meanwhile.
    if (state == UpdateState.None) {
        LaunchedEffect(Unit) { onLater() }
        return
    }
    UpdateScreen(
        state = state,
        language = updates.language,
        onDownload = { if (updates.canInstall()) updates.download() else askPermission = true },
        onInstall = { if (updates.canInstall()) launchInstaller() else askPermission = true },
        onRetry = updates::retry,
        onLater = onLater
    )
    // Straight to the installer when the download completes.
    LaunchedEffect(state is UpdateState.ReadyToInstall) {
        if (state is UpdateState.ReadyToInstall && updates.canInstall()) launchInstaller()
    }
    if (askPermission) {
        InstallPermissionDialog(
            onOpenSettings = {
                askPermission = false
                context.startSafely(updates.unknownSourcesIntent())
            },
            onDismiss = { askPermission = false }
        )
    }
}

/** Some Fire OS builds lack a system screen: the dialog text explains the manual path. */
private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("FireDNS", "No activity for ${intent.action}", e)
    }
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
        onCheckUpdatesChange = { enabled ->
            container.appScope.launch {
                repository.setCheckUpdates(enabled)
                container.updateManager.onCheckUpdatesChanged(enabled)
            }
        },
        onOpenInfo = onOpenInfo
    )
}
