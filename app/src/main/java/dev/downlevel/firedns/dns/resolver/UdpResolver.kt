package dev.downlevel.firedns.dns.resolver

import dev.downlevel.firedns.dns.u16
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Classic DNS over UDP/53. [protect] excludes the socket from the VPN (VpnService.protect):
 * redundant with addDisallowedApplication, but avoids loops if the exclusion were not honored.
 */
class UdpResolver(
    private val server: InetAddress,
    private val port: Int = 53,
    private val timeoutMs: Int = 3000,
    private val protect: (DatagramSocket) -> Boolean = { true }
) : DnsResolver {

    override suspend fun resolve(query: ByteArray): ByteArray = withContext(Dispatchers.IO) { exchange(query) }

    private fun exchange(query: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            if (!protect(socket)) throw IOException("protect() failed")
            socket.soTimeout = timeoutMs
            socket.send(DatagramPacket(query, query.size, server, port))
            val buf = ByteArray(MAX_RESPONSE)
            val packet = DatagramPacket(buf, buf.size)
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            while (System.nanoTime() < deadline) {
                packet.length = buf.size
                socket.receive(packet)
                // Drop answers with a different ID (late or spoofed).
                if (packet.length >= 12 && buf.u16(0) == query.u16(0)) return buf.copyOf(packet.length)
            }
        }
        throw SocketTimeoutException("No valid answer from ${server.hostAddress}")
    }

    private companion object {
        const val MAX_RESPONSE = 65535
    }
}
