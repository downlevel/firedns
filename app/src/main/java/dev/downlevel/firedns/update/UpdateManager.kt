package dev.downlevel.firedns.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dev.downlevel.firedns.BuildConfig
import dev.downlevel.firedns.data.SettingsRepository
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object None : UpdateState

    data class Available(val manifest: UpdateManifest) : UpdateState

    /** [progress] 0..1, or -1 when the size is unknown. */
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState

    data class ReadyToInstall(val manifest: UpdateManifest) : UpdateState

    data class Failed(val manifest: UpdateManifest) : UpdateState
}

val UpdateState.manifest: UpdateManifest?
    get() = when (this) {
        UpdateState.None -> null
        is UpdateState.Available -> manifest
        is UpdateState.Downloading -> manifest
        is UpdateState.ReadyToInstall -> manifest
        is UpdateState.Failed -> manifest
    }

/**
 * Self-update: daily check of `version.json` ([UpdateJobService]), APK download with SHA-256
 * check, then Android's own installer. A silent install is not possible without root: the
 * user always confirms. Android also refuses an APK not signed with the same release key.
 */
class UpdateManager(
    context: Context,
    private val settings: SettingsRepository,
    private val checker: UpdateChecker,
    private val scope: CoroutineScope,
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE
) {
    private val context = context.applicationContext
    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val apkFile: File get() = File(File(context.cacheDir, UPDATES_DIR), APK_NAME)

    val language: String get() = Locale.getDefault().language

    /**
     * Looks for a newer release. With [notify] (background check) a notification is posted
     * once per version. Does nothing if the user turned the check off or in debug builds.
     */
    suspend fun check(notify: Boolean) {
        if (!BuildConfig.UPDATE_CHECK || !settings.settings.first().checkUpdates) {
            _state.value = UpdateState.None
            return
        }
        // Do not interrupt a download or an install in progress.
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.ReadyToInstall) return
        val latest = checker.fetchLatest() ?: return
        if (!latest.isNewerThan(installedVersionCode) || !latest.hasTrustedApkUrl()) {
            _state.value = UpdateState.None
            return
        }
        _state.value = UpdateState.Available(latest)
        if (notify && settings.settings.first().notifiedUpdateVersionCode < latest.versionCode) {
            UpdateNotifications.showAvailable(context, latest.versionName)
            settings.setNotifiedUpdateVersionCode(latest.versionCode)
        }
    }

    fun download() {
        val manifest = _state.value.manifest ?: return
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(manifest, 0f)
        scope.launch {
            _state.value = try {
                apkFile.parentFile?.mkdirs()
                checker.download(manifest, apkFile) { progress ->
                    _state.value = UpdateState.Downloading(manifest, progress)
                }
                UpdateState.ReadyToInstall(manifest)
            } catch (e: IOException) {
                Log.w("FireDNS", "Update download failed", e)
                UpdateState.Failed(manifest)
            }
        }
    }

    /** Android 8+: the user must allow FireDNS to install apps (a per-app system setting). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** System screen to allow installs from FireDNS; may be missing on some Fire OS builds. */
    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Android's installer for the downloaded APK (the same route Downloader uses on Fire TV).
     * `null` if the file is gone, e.g. the cache was cleared.
     */
    fun installIntent(): Intent? {
        val file = apkFile.takeIf { it.exists() } ?: return null.also { resetToAvailable() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun retry() = resetToAvailable()

    /** Notes of the installed version, bundled in `assets/release-notes/<versionName>.json`. */
    fun installedNotes(): List<String> = try {
        val text = context.assets.open("release-notes/${BuildConfig.VERSION_NAME}.json")
            .bufferedReader()
            .use { it.readText() }
        ReleaseNotes.pick(ReleaseNotes.parse(text), language)
    } catch (e: IOException) {
        emptyList()
    }

    /** The user saw "What's new" (or there was nothing to show): do not show it again. */
    suspend fun markInstalledVersionSeen() {
        UpdateNotifications.cancelUpdated(context)
        if (settings.settings.first().lastSeenVersionCode != installedVersionCode) {
            settings.setLastSeenVersionCode(installedVersionCode)
        }
    }

    /** Turning the check off also drops what was found and its notification. */
    fun onCheckUpdatesChanged(enabled: Boolean) {
        if (enabled) {
            UpdateScheduler.schedule(context)
            scope.launch { check(notify = false) }
        } else {
            UpdateScheduler.cancel(context)
            UpdateNotifications.cancelAvailable(context)
            _state.value = UpdateState.None
            apkFile.parentFile?.deleteRecursively()
        }
    }

    /** Called at startup: an APK left in the cache is useless once installed. */
    fun cleanUp() {
        if (_state.value !is UpdateState.ReadyToInstall && _state.value !is UpdateState.Downloading) {
            apkFile.parentFile?.deleteRecursively()
        }
    }

    private fun resetToAvailable() {
        _state.value.manifest?.let { _state.value = UpdateState.Available(it) }
    }

    private companion object {
        const val UPDATES_DIR = "updates"
        const val APK_NAME = "firedns.apk"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
