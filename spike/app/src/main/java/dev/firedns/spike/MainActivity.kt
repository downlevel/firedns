package dev.firedns.spike

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.random.Random

/** Utility UI for the spike: D-pad buttons + counters + log. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var summary: TextView
    private lateinit var log: TextView

    private val refresh = object : Runnable {
        override fun run() {
            summary.text = SpikeStats.summary()
            log.text = SpikeStats.lines().joinToString("\n")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
        if (savedInstanceState == null) {
            SpikeStats.log(
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT} · build ${Build.DISPLAY}",
            )
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VPN) return
        if (resultCode == RESULT_OK) {
            startForegroundService(Intent(this, SpikeVpnService::class.java))
        } else {
            SpikeStats.log("VPN permission denied")
        }
    }

    private fun buildLayout(): View {
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // TV overscan safe margins.
            setPadding(px(48), px(27), px(48), px(27))
        }
        root.addView(
            TextView(this).apply {
                text = "FireDNS — local VPN spike"
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
            },
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(12), 0, px(12))
        }
        fun button(label: String, action: () -> Unit): Button =
            Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            }.also { buttons.addView(it) }

        val start = button("Start VPN") { requestVpnAndStart() }
        button("Stop VPN") {
            startService(Intent(this, SpikeVpnService::class.java).setAction(SpikeVpnService.ACTION_STOP))
        }
        button("App exclusion test") { testAppExclusion() }
        button("Network info") { logNetworkInfo() }
        button("Clear log") { SpikeStats.reset() }
        root.addView(buttons)

        summary = TextView(this).apply { textSize = 18f }
        root.addView(summary)

        log = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, px(8), 0, 0)
        }
        root.addView(
            ScrollView(this).apply {
                isFocusable = true
                addView(log)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        start.requestFocus()
        return root
    }

    private fun requestVpnAndStart() {
        val consent = VpnService.prepare(this)
        if (consent != null) {
            startActivityForResult(consent, REQ_VPN)
        } else {
            onActivityResult(REQ_VPN, RESULT_OK, null)
        }
    }

    /**
     * Resolves a unique name from the app: if it shows up among the tunnel queries,
     * addDisallowedApplication is not honored (loop risk in the real app).
     */
    private fun testAppExclusion() {
        if (!SpikeStats.running) {
            SpikeStats.log("Start the VPN first")
            return
        }
        val host = "app-${Random.nextInt(100_000, 999_999)}.example.com"
        Thread {
            val result = try {
                InetAddress.getAllByName(host).joinToString { it.hostAddress ?: "?" }
            } catch (_: UnknownHostException) {
                "not resolved"
            }
            val control = try {
                InetAddress.getByName("example.com").hostAddress
            } catch (_: UnknownHostException) {
                null
            }
            Thread.sleep(1500)
            val leaked = SpikeStats.lines().any { host in it }
            SpikeStats.log(
                if (leaked) {
                    "✗ EXCLUSION NOT HONORED: the app query ($host) went through the tunnel"
                } else {
                    "✓ Exclusion OK: $host ($result) did not go through the tunnel; " +
                        "example.com from the app → ${control ?: "ERROR"}"
                },
            )
        }.start()
    }

    private fun logNetworkInfo() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val active = cm.activeNetwork
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network)
            val lp = cm.getLinkProperties(network)
            val kind = when {
                caps == null -> "?"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "other"
            }
            val dns = lp?.dnsServers?.joinToString { it.hostAddress ?: "?" } ?: "-"
            val privateDns = if (lp != null && lp.isPrivateDnsActive) {
                "active (${lp.privateDnsServerName ?: "automatic"})"
            } else {
                "inactive"
            }
            SpikeStats.log("Network $kind: DNS [$dns] · Private DNS $privateDns · default=${network == active}")
        }
        val mode = Settings.Global.getString(contentResolver, "private_dns_mode")
        SpikeStats.log("private_dns_mode = ${mode ?: "(not set)"}")
    }

    companion object {
        private const val REQ_VPN = 1
        private const val REQ_NOTIFICATIONS = 2
    }
}
