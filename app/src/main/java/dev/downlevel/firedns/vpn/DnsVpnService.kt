package dev.downlevel.firedns.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import dev.downlevel.firedns.FireDnsApp
import dev.downlevel.firedns.MainActivity
import dev.downlevel.firedns.R
import dev.downlevel.firedns.dns.DnsCache
import dev.downlevel.firedns.dns.DnsForwarder
import dev.downlevel.firedns.dns.FallbackPolicy
import dev.downlevel.firedns.dns.Ipv4
import dev.downlevel.firedns.dns.resolver.ChainResolver
import dev.downlevel.firedns.dns.resolver.DnsResolver
import dev.downlevel.firedns.dns.resolver.ResolverFactory
import dev.downlevel.firedns.dns.resolver.UdpResolver
import java.io.FileDescriptor
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Local VPN that routes only DNS addresses into the TUN (the virtual DNS and the network's
 * public DNS servers, which Fire OS adds to the VPN) and answers queries with the active profile.
 * All other traffic, IPv6 included, stays outside the tunnel.
 *
 * If the chosen DNS stops answering it switches to the network DNS ([FallbackPolicy]); when the
 * network changes it updates routes and fallback DNS without the user restarting the VPN.
 */
class DnsVpnService : VpnService() {

    private val container get() = (application as FireDnsApp).container

    private var scope: CoroutineScope? = null

    @Volatile
    private var tun: ParcelFileDescriptor? = null
    private var readerJob: Job? = null
    private var forwarder: DnsForwarder? = null
    private var interceptedPublicDns: List<String> = emptyList()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val writeLock = Any()

    private val cache = DnsCache()
    private val primary = AtomicReference<DnsResolver>()
    private val networkFallback = AtomicReference<DnsResolver?>()
    private val fallbackEnabled = AtomicBoolean(true)
    private val policy = FallbackPolicy(onChange = { refreshStatus() })

    @Volatile
    private var profileName: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            container.vpnStateStore.set(VpnState.Off)
            // Also from the notification button: it must not come back on at the next boot.
            container.appScope.launch { container.settingsRepository.setVpnDesiredOn(false) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(notification(VpnState.Starting))
        if (tun == null) startTunnel()
        return START_STICKY
    }

    override fun onRevoke() {
        stopTunnel()
        container.vpnStateStore.set(VpnState.Error(VpnError.REVOKED))
        // The user chose another VPN: do not fight over it at the next start.
        container.appScope.launch { container.settingsRepository.setVpnDesiredOn(false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (tun != null) {
            stopTunnel()
            container.vpnStateStore.set(VpnState.Off)
        }
        super.onDestroy()
    }

    private fun startTunnel() {
        val store = container.vpnStateStore
        store.set(VpnState.Starting)

        val networkDns = currentNetworkDns()
        val publicDns = publicIpv4(networkDns)
        val pfd = establish(publicDns)
        if (pfd == null) {
            // establish() returns null when VPN consent is gone.
            store.set(VpnState.Error(VpnError.PERMISSION_DENIED))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        tun = pfd
        interceptedPublicDns = publicDns
        networkFallback.set(networkResolver(networkDns))

        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        val factory = ResolverFactory(container.httpClient, protect = { protect(it) })
        val dnsForwarder = DnsForwarder(
            scope = serviceScope,
            interceptedAddresses = interceptedSet(publicDns),
            cache = cache,
            primary = { primary.get() },
            output = ::writeToTun,
            fallback = { if (fallbackEnabled.get()) networkFallback.get() else null },
            policy = policy
        ).also { forwarder = it }

        serviceScope.launch {
            container.settingsRepository.settings.map { it.fallbackEnabled }.distinctUntilChanged().collect { enabled ->
                fallbackEnabled.set(enabled)
                if (!enabled) policy.reset()
            }
        }
        serviceScope.launch {
            var started = false
            // Hot profile switch: new resolver, cache cleared, fallback reset.
            container.profileRepository.activeProfile.collect { profile ->
                primary.set(factory.create(profile))
                cache.clear()
                profileName = profile.name
                policy.reset()
                if (!started) {
                    started = true
                    readerJob = startReader(serviceScope, pfd, dnsForwarder)
                    watchNetwork(serviceScope, networkDns)
                }
                refreshStatus()
            }
        }
    }

    private fun establish(publicDns: List<String>): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32)
            // With no IPv6 address in the VPN Android would block all IPv6 traffic: let it through.
            .allowFamily(OsConstants.AF_INET6)
            .setConfigureIntent(openAppIntent())
        publicDns.forEach { builder.addRoute(it, 32) }
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot exclude the app from the VPN", e)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        return try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish() failed", e)
            null
        }
    }

    /**
     * Watches the app's default network (excluded from the VPN, so the real one). When its DNS
     * servers change it updates the fallback DNS and, if different routes are needed, re-creates the TUN.
     */
    @OptIn(FlowPreview::class)
    private fun watchNetwork(serviceScope: CoroutineScope, initialDns: List<InetAddress>) {
        val dnsUpdates = MutableStateFlow(initialDns)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val dns = usableDns(linkProperties.dnsServers)
                if (dns.isNotEmpty()) dnsUpdates.value = dns
            }
        }
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
        networkCallback = callback
        serviceScope.launch {
            dnsUpdates.drop(1).debounce(NETWORK_DEBOUNCE_MS).collect { applyNetworkDns(it) }
        }
    }

    private fun applyNetworkDns(networkDns: List<InetAddress>) {
        networkFallback.set(networkResolver(networkDns))
        val publicDns = publicIpv4(networkDns)
        if (publicDns != interceptedPublicDns) rebuildTunnel(publicDns)
        // DoH connections opened on the previous network: better to reopen them right away.
        container.httpClient.connectionPool.evictAll()
        policy.reset()
    }

    /** New routes: establish() again, move reading to the new fd, close the old one. */
    private fun rebuildTunnel(publicDns: List<String>) {
        val serviceScope = scope ?: return
        val dnsForwarder = forwarder ?: return
        val newTun = establish(publicDns) ?: return
        val oldTun = tun
        val oldReader = readerJob
        tun = newTun
        dnsForwarder.interceptedAddresses = interceptedSet(publicDns)
        interceptedPublicDns = publicDns
        readerJob = startReader(serviceScope, newTun, dnsForwarder)
        oldReader?.cancel()
        try {
            oldTun?.close()
        } catch (_: IOException) {
        }
    }

    private fun currentNetworkDns(): List<InetAddress> {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return emptyList()
        val isVpn = cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        if (isVpn) return emptyList()
        return usableDns(cm.getLinkProperties(network)?.dnsServers.orEmpty())
    }

    private fun usableDns(servers: List<InetAddress>): List<InetAddress> = servers
        .filterNot { it.isLoopbackAddress || it.isAnyLocalAddress }
        // Link-local IPv6 (fe80::) needs the interface: not usable for fallback.
        .filterNot { it is Inet6Address && it.isLinkLocalAddress }
        .filterNot { it.hostAddress == VIRTUAL_DNS }
        .distinct()

    /**
     * Public IPv4 DNS servers of the network (e.g. 8.8.8.8 set manually on the Wi-Fi). Fire OS adds
     * them to the VPN: routing them into the tunnel means no query bypasses the chosen profile. Local
     * network addresses (router, Pi-hole) stay outside so the rest of the traffic to them is not blocked.
     */
    private fun publicIpv4(servers: List<InetAddress>): List<String> = servers
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isSiteLocalAddress || it.isLinkLocalAddress }
        .mapNotNull { it.hostAddress }

    private fun interceptedSet(publicDns: List<String>): Set<Int> =
        (publicDns.map { Ipv4.address(it) } + Ipv4.address(VIRTUAL_DNS)).toSet()

    /** Fallback: the network DNS servers, tried in order. `null` if the network has none. */
    private fun networkResolver(servers: List<InetAddress>): DnsResolver? = servers
        .takeIf { it.isNotEmpty() }
        ?.let { list ->
            ChainResolver(
                list.map { UdpResolver(it, timeoutMs = FALLBACK_TIMEOUT_MS, protect = ::protect) }
            )
        }

    private fun startReader(serviceScope: CoroutineScope, pfd: ParcelFileDescriptor, dnsForwarder: DnsForwarder): Job =
        serviceScope.launch { readLoop(pfd.fileDescriptor, dnsForwarder) }

    private fun CoroutineScope.readLoop(fd: FileDescriptor, dnsForwarder: DnsForwarder) {
        val buf = ByteArray(MAX_PACKET)
        // The TUN fd is non-blocking (API 28): poll() with a timeout so the loop can exit on close.
        val pollFd = StructPollfd().apply {
            this.fd = fd
            events = OsConstants.POLLIN.toShort()
        }
        while (isActive) {
            try {
                pollFd.revents = 0
                if (Os.poll(arrayOf(pollFd), POLL_TIMEOUT_MS) <= 0) continue
                val length = Os.read(fd, buf, 0, buf.size)
                if (length > 0) dnsForwarder.handle(buf, length)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue
                if (isActive) Log.e(TAG, "TUN read failed", e)
                return
            } catch (e: IOException) {
                if (isActive) Log.e(TAG, "TUN read failed", e)
                return
            }
        }
    }

    private fun writeToTun(packet: ByteArray) {
        val fd = tun?.fileDescriptor ?: return
        try {
            synchronized(writeLock) { Os.write(fd, packet, 0, packet.size) }
        } catch (e: ErrnoException) {
            Log.w(TAG, "TUN write failed: ${e.message}")
        } catch (e: IOException) {
            Log.w(TAG, "TUN write failed: ${e.message}")
        }
    }

    private fun stopTunnel() {
        networkCallback?.let { callback ->
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        scope?.cancel()
        scope = null
        readerJob = null
        forwarder = null
        val oldTun = tun
        tun = null
        try {
            oldTun?.close()
        } catch (_: IOException) {
        }
        cache.clear()
        policy.reset()
    }

    /** State and notification consistent with fallback; ignored when the tunnel is down. */
    private fun refreshStatus() {
        if (tun == null || profileName == null) return
        val state = if (policy.isActive) VpnState.Fallback else VpnState.Active
        container.vpnStateStore.set(state)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(state: VpnState): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val title = when (state) {
            VpnState.Active -> getString(R.string.notif_active, profileName.orEmpty())
            VpnState.Fallback -> getString(R.string.notif_fallback)
            else -> getString(R.string.notif_starting)
        }
        val stop = PendingIntent.getService(this, 1, stopIntent(this), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(openAppIntent())
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_turn_off), stop).build())
            .setOngoing(true)
            .build()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private const val TAG = "FireDNS"
        private const val ACTION_START = "dev.downlevel.firedns.START"
        private const val ACTION_STOP = "dev.downlevel.firedns.STOP"

        /** Virtual DNS: no real server, the app answers in its place. */
        const val VIRTUAL_DNS = "10.111.222.1"
        private const val TUN_ADDRESS = "10.111.222.2"
        private const val MTU = 1500
        private const val MAX_PACKET = 65535
        private const val POLL_TIMEOUT_MS = 500
        private const val FALLBACK_TIMEOUT_MS = 2000
        private const val NETWORK_DEBOUNCE_MS = 1000L
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1

        fun startIntent(context: Context): Intent = Intent(context, DnsVpnService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent = Intent(context, DnsVpnService::class.java).setAction(ACTION_STOP)
    }
}
