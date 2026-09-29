package dev.downlevel.firedns.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.ui.components.screenPadding
import dev.downlevel.firedns.ui.theme.FireDnsColors

/** Informational screen: nothing focusable, leave with the Back key. */
@Composable
fun InfoScreen(versionName: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().screenPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.info_version, versionName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FireDnsColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        InfoLine(stringResource(R.string.info_source), stringResource(R.string.repo_url))
        InfoLine(stringResource(R.string.info_license_label), stringResource(R.string.info_license))
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.info_disclaimer),
            style = MaterialTheme.typography.bodyLarge,
            color = FireDnsColors.TextSecondary,
            modifier = Modifier.widthIn(max = 760.dp)
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = FireDnsColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
