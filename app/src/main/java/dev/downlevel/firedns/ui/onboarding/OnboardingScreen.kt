package dev.downlevel.firedns.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.downlevel.firedns.R
import dev.downlevel.firedns.ui.components.FireButton
import dev.downlevel.firedns.ui.components.screenPadding
import dev.downlevel.firedns.ui.theme.FireDnsColors

@Composable
fun OnboardingScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val continueFocus = remember { FocusRequester() }
    Box(modifier.fillMaxSize().screenPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            listOf(
                R.string.onboarding_point_what,
                R.string.onboarding_point_vpn,
                R.string.onboarding_point_privacy
            ).forEach { OnboardingPoint(stringResource(it)) }
            Spacer(Modifier.height(28.dp))
            FireButton(
                stringResource(R.string.action_continue),
                onContinue,
                modifier = Modifier.focusRequester(continueFocus)
            )
        }
    }
    LaunchedEffect(Unit) { continueFocus.requestFocus() }
}

@Composable
private fun OnboardingPoint(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            Modifier
                .padding(top = 9.dp)
                .size(8.dp)
                .background(FireDnsColors.Fire, CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
