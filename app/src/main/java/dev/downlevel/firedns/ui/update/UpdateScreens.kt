package dev.downlevel.firedns.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.ui.components.FireButton
import dev.downlevel.firedns.ui.components.FireButtonStyle
import dev.downlevel.firedns.ui.components.FireDialog
import dev.downlevel.firedns.ui.components.screenPadding
import dev.downlevel.firedns.ui.theme.FireDnsColors
import dev.downlevel.firedns.update.UpdateState
import dev.downlevel.firedns.update.manifest

/** Shown once after an update: the notes of the version now installed. */
@Composable
fun WhatsNewScreen(versionName: String, notes: List<String>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    NotesLayout(
        title = stringResource(R.string.whatsnew_title),
        subtitle = stringResource(R.string.whatsnew_subtitle, versionName),
        notes = notes,
        modifier = modifier
    ) {
        FireButton(stringResource(R.string.whatsnew_done), onDone, Modifier.focusRequester(focus))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * A newer version: its notes, then download → Android's installer. The installer is opened
 * as soon as the download finishes; "Install" reopens it if the user backed out.
 */
@Composable
fun UpdateScreen(
    state: UpdateState,
    language: String,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manifest = state.manifest ?: return
    val focus = remember { FocusRequester() }
    NotesLayout(
        title = stringResource(R.string.update_title, manifest.versionName),
        subtitle = stringResource(R.string.update_subtitle),
        notes = manifest.notesFor(language),
        modifier = modifier
    ) {
        when (state) {
            is UpdateState.Available -> {
                FireButton(stringResource(R.string.update_now), onDownload, Modifier.focusRequester(focus))
                FireButton(stringResource(R.string.update_later), onLater, style = FireButtonStyle.Secondary)
            }
            is UpdateState.Downloading -> DownloadProgress(state.progress)
            is UpdateState.ReadyToInstall -> {
                FireButton(stringResource(R.string.update_install), onInstall, Modifier.focusRequester(focus))
                FireButton(stringResource(R.string.update_later), onLater, style = FireButtonStyle.Secondary)
            }
            is UpdateState.Failed -> {
                Text(
                    stringResource(R.string.update_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FireDnsColors.Error
                )
                FireButton(stringResource(R.string.action_retry), onRetry, Modifier.focusRequester(focus))
            }
            UpdateState.None -> Unit
        }
    }
    // A new button appears at each step: move the focus to it.
    LaunchedEffect(state::class) { runCatching { focus.requestFocus() } }
}

/** Android requires the user to allow FireDNS to install apps; the system screen may be missing. */
@Composable
fun InstallPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    FireDialog(onDismiss) {
        Text(stringResource(R.string.update_permission_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.update_permission_message),
            style = MaterialTheme.typography.bodyLarge,
            color = FireDnsColors.TextSecondary
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FireButton(
                stringResource(R.string.update_permission_open),
                onOpenSettings,
                Modifier.focusRequester(focus)
            )
            FireButton(stringResource(R.string.action_cancel), onDismiss, style = FireButtonStyle.Secondary)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun NotesLayout(
    title: String,
    subtitle: String,
    notes: List<String>,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().screenPadding()) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = FireDnsColors.TextSecondary)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier.widthIn(max = 860.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            notes.forEach { note ->
                Row {
                    Text("•", style = MaterialTheme.typography.titleMedium, color = FireDnsColors.Fire)
                    Spacer(Modifier.width(12.dp))
                    Text(note, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    Column(Modifier.widthIn(min = 360.dp, max = 520.dp)) {
        Text(
            if (progress >= 0f) {
                stringResource(R.string.update_downloading_percent, (progress * 100).toInt())
            } else {
                stringResource(R.string.update_downloading)
            },
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        val shape = RoundedCornerShape(50)
        Box(Modifier.fillMaxWidth().height(8.dp).background(FireDnsColors.Surface, shape)) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(FireDnsColors.Fire, shape)
            )
        }
    }
}
