package dev.firedns.spike

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** State shared between service and activity (same process): enough for a spike. */
object SpikeStats {
    const val TAG = "FireDnsSpike"
    private const val MAX_LOG_LINES = 300

    @Volatile var running = false
    val queries = AtomicLong()
    val answered = AtomicLong()
    val failed = AtomicLong()
    val otherPackets = AtomicLong()
    val totalLatencyMs = AtomicLong()

    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    fun log(msg: String) {
        Log.i(TAG, msg)
        synchronized(lines) {
            lines.addFirst("${timeFormat.format(Date())}  $msg")
            while (lines.size > MAX_LOG_LINES) lines.pollLast()
        }
    }

    /** Log lines, most recent first. */
    fun lines(): List<String> = synchronized(lines) { lines.toList() }

    fun reset() {
        queries.set(0)
        answered.set(0)
        failed.set(0)
        otherPackets.set(0)
        totalLatencyMs.set(0)
        synchronized(lines) { lines.clear() }
    }

    fun summary(): String {
        val ok = answered.get()
        val avg = if (ok > 0) totalLatencyMs.get() / ok else 0
        return "VPN: ${if (running) "ON" else "off"}   ·   query: ${queries.get()}   " +
            "answers: $ok   errors: ${failed.get()}   avg latency: $avg ms   " +
            "non-DNS packets in tunnel: ${otherPackets.get()}"
    }
}
