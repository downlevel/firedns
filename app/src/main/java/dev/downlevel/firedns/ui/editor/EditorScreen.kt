package dev.downlevel.firedns.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.data.DnsAddress
import dev.downlevel.firedns.data.DnsProtocol
import dev.downlevel.firedns.ui.components.FireButton
import dev.downlevel.firedns.ui.components.FireButtonStyle
import dev.downlevel.firedns.ui.components.FireTextField
import dev.downlevel.firedns.ui.components.ProtocolBadge
import dev.downlevel.firedns.ui.components.screenPadding
import dev.downlevel.firedns.ui.theme.FireDnsColors

@Composable
fun EditorScreen(
    isNew: Boolean,
    form: EditorForm,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nameFocus = remember { FocusRequester() }
    Row(modifier.fillMaxSize().screenPadding(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1.4f)) {
            Text(
                stringResource(if (isNew) R.string.editor_title_new else R.string.editor_title_edit),
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(Modifier.height(28.dp))
            FireTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.editor_name),
                placeholder = stringResource(R.string.editor_name_hint),
                focusRequester = nameFocus
            )
            Spacer(Modifier.height(20.dp))
            FireTextField(
                value = form.address,
                onValueChange = onAddressChange,
                label = stringResource(R.string.editor_address),
                placeholder = stringResource(R.string.editor_address_hint),
                isError = form.showAddressError,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            )
            AddressFeedback(form, Modifier.padding(top = 10.dp).height(28.dp))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FireButton(stringResource(R.string.editor_save), onSave, enabled = form.canSave)
                FireButton(stringResource(R.string.action_cancel), onCancel, style = FireButtonStyle.Secondary)
            }
        }
        HelpPanel(Modifier.weight(1f).padding(top = 76.dp))
    }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
}

@Composable
private fun AddressFeedback(form: EditorForm, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val parsed = form.parsedAddress
        when {
            parsed != null -> {
                Text(
                    stringResource(R.string.editor_detected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FireDnsColors.TextSecondary
                )
                Spacer(Modifier.width(10.dp))
                ProtocolBadge(if (parsed is DnsAddress.Doh) DnsProtocol.DOH else DnsProtocol.UDP)
            }
            form.showAddressError -> Text(
                stringResource(R.string.editor_address_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = FireDnsColors.Error
            )
        }
    }
}

@Composable
private fun HelpPanel(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(FireDnsColors.Surface, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.editor_help_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.editor_help_nextdns), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.editor_help_pihole), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.editor_help_note),
            style = MaterialTheme.typography.bodyMedium,
            color = FireDnsColors.TextSecondary
        )
    }
}
