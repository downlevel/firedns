package dev.firedns.spike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Local VPN that routes only the virtual DNS [VIRTUAL_DNS] into the TUN and forwards
 * each query over UDP to [UPSTREAM_DNS]. All other traffic stays outside the tunnel.
 */
class SpikeVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var workers: ExecutorService? = null
    @Volatile private var running = false
    private val writeLock = Any()
    private val upstream: InetAddress by lazy { InetAddress.getByName(UPSTREAM_DNS) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel("stopped by the user")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startTunnel()
        return START_STICKY
    }

    override fun onRevoke() {
        stopTunnel("revoked by the system (another VPN turned on?)")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel("service destroyed")
        super.onDestroy()
    }

    private fun startTunnel() {
        startInForeground()
        if (running) return

        val builder = Builder()
            .setSession("FireDNS spike")
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32)
        try {
            // This app's upstream traffic must not re-enter the tunnel.
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            SpikeStats.log("addDisallowedApplication failed: ${e.message}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            SpikeStats.log("establish() threw ${e.javaClass.simpleName}: ${e.message}")
            null
        }
        if (pfd == null) {
            SpikeStats.log("establish() failed: VPN permission missing or revoked")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tun = pfd
        running = true
        SpikeStats.running = true
        workers = Executors.newFixedThreadPool(WORKER_THREADS)
        reader = Thread({ readLoop(pfd.fileDescriptor) }, "tun-reader").also { it.start() }
        SpikeStats.log("Tunnel up: virtual DNS $VIRTUAL_DNS → upstream $UPSTREAM_DNS (UDP)")
    }

    private fun stopTunnel(reason: String) {
        if (!running) return
        running = false
        SpikeStats.running = false
        try {
            reader?.join(POLL_TIMEOUT_MS * 2L)
        } catch (_: InterruptedException) {
        }
        reader = null
        workers?.shutdownNow()
        workers = null
        try {
            tun?.close()
        } catch (_: IOException) {
        }
        tun = null
        SpikeStats.log("Tunnel closed: $reason")
    }

    private fun readLoop(fd: FileDescriptor) {
        val buf = ByteArray(MAX_PACKET)
        // The TUN fd is non-blocking on API 28: poll() + read().
        val pollFd = StructPollfd().apply {
            this.fd = fd
            events = OsConstants.POLLIN.toShort()
        }
        while (running) {
            try {
                pollFd.revents = 0
                if (Os.poll(arrayOf(pollFd), POLL_TIMEOUT_MS) <= 0) continue
                val len = Os.read(fd, buf, 0, buf.size)
                if (len > 0) handlePacket(buf.copyOf(len))
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue
                if (running) SpikeStats.log("TUN read error: ${e.message}")
                break
            } catch (e: IOException) {
                if (running) SpikeStats.log("TUN read error: ${e.message}")
                break
            }
        }
    }

    private fun handlePacket(pkt: ByteArray) {
        val version = (pkt[0].toInt() shr 4) and 0xF
        if (version != 4 || pkt.size < 28) {
            countOther("IPv$version, ${pkt.size} byte")
            return
        }
        val ihl = (pkt[0].toInt() and 0xF) * 4
        val protocol = pkt[9].toInt() and 0xFF
        val dst = pkt.copyOfRange(16, 20)
        if (protocol != PROTO_UDP || !dst.contentEquals(VIRTUAL_DNS_BYTES) || pkt.size < ihl + 8) {
            countOther("proto=$protocol dst=${InetAddress.getByAddress(dst).hostAddress}")
            return
        }
        val srcPort = Packets.u16(pkt, ihl)
        val dstPort = Packets.u16(pkt, ihl + 2)
        if (dstPort != 53) {
            countOther("UDP to port $dstPort")
            return
        }
        val end = minOf(pkt.size, ihl + Packets.u16(pkt, ihl + 4))
        if (end <= ihl + 8) return
        val query = pkt.copyOfRange(ihl + 8, end)
        val client = pkt.copyOfRange(12, 16)

        SpikeStats.queries.incrementAndGet()
        try {
            workers?.execute { forward(client, srcPort, query) }
        } catch (_: RejectedExecutionException) {
            // Tunnel shutting down.
        }
    }

    private fun forward(client: ByteArray, clientPort: Int, query: ByteArray) {
        val question = DnsMessages.describeQuestion(query)
        val start = SystemClock.elapsedRealtime()
        try {
            DatagramSocket().use { socket ->
                // Redundant with addDisallowedApplication, but it is the pattern for the real app.
                if (!protect(socket)) throw IOException("protect() returned false")
                socket.soTimeout = UPSTREAM_TIMEOUT_MS
                socket.send(DatagramPacket(query, query.size, upstream, 53))
                val buf = ByteArray(MAX_PACKET)
                val response = DatagramPacket(buf, buf.size)
                socket.receive(response)
                val answer = buf.copyOf(response.length)
                writeToTun(Packets.buildUdpResponse(VIRTUAL_DNS_BYTES, client, clientPort, answer))

                val ms = SystemClock.elapsedRealtime() - start
                SpikeStats.answered.incrementAndGet()
                SpikeStats.totalLatencyMs.addAndGet(ms)
                val oversize = if (answer.size + 28 > MTU) " (over MTU!)" else ""
                SpikeStats.log("$question → ${DnsMessages.rcode(answer)} in $ms ms$oversize")
            }
        } catch (e: Exception) {
            SpikeStats.failed.incrementAndGet()
            SpikeStats.log("$question ✗ ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun writeToTun(packet: ByteArray) {
        val fd = tun?.fileDescriptor ?: return
        synchronized(writeLock) { Os.write(fd, packet, 0, packet.size) }
    }

    private fun countOther(desc: String) {
        val n = SpikeStats.otherPackets.incrementAndGet()
        if (n <= MAX_OTHER_LOGGED) SpikeStats.log("Non-DNS packet in tunnel: $desc")
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "FireDNS spike", NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FireDNS spike running")
            .setContentText("DNS → $UPSTREAM_DNS")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "dev.firedns.spike.STOP"
        const val UPSTREAM_DNS = "1.1.1.1"
        const val VIRTUAL_DNS = "10.111.222.1"
        private const val TUN_ADDRESS = "10.111.222.2"
        private val VIRTUAL_DNS_BYTES = byteArrayOf(10, 111, 222.toByte(), 1)
        private const val MTU = 1500
        private const val MAX_PACKET = 65535
        private const val POLL_TIMEOUT_MS = 500
        private const val UPSTREAM_TIMEOUT_MS = 3000
        private const val WORKER_THREADS = 8
        private const val MAX_OTHER_LOGGED = 20
        private const val PROTO_UDP = 17
        private const val CHANNEL_ID = "spike"
        private const val NOTIFICATION_ID = 1
    }
}
