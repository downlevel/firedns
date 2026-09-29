package dev.downlevel.firedns

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import dev.downlevel.firedns.ui.FireDnsRoot
import dev.downlevel.firedns.ui.theme.FireDnsTheme

class MainActivity : ComponentActivity() {

    // Without this permission the VPN still works, but the status notification is hidden (Android 13+).
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FireDnsApp).container
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            FireDnsTheme {
                FireDnsRoot(container)
            }
        }
    }
}
