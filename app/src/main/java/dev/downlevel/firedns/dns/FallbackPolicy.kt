package dev.downlevel.firedns.dns

/**
 * Decides when to switch to the network DNS: after [threshold] consecutive failures of the chosen
 * DNS it enters fallback; from there it retries the chosen DNS at most every [retryIntervalMs] and
 * goes back on the first success. [onChange] receives `true` on entering fallback and `false` on leaving.
 */
class FallbackPolicy(
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val retryIntervalMs: Long = DEFAULT_RETRY_INTERVAL_MS,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onChange: (Boolean) -> Unit = {}
) {
    private var consecutiveFailures = 0
    private var nextProbeAt = 0L

    @Volatile
    var isActive = false
        private set

    /** Always `true` outside fallback; in fallback `true` once every [retryIntervalMs] (probe). */
    @Synchronized
    fun shouldTryPrimary(): Boolean {
        if (!isActive) return true
        val now = nowMillis()
        if (now < nextProbeAt) return false
        nextProbeAt = now + retryIntervalMs
        return true
    }

    fun onPrimarySuccess() = reset()

    fun onPrimaryFailure() {
        val entered = synchronized(this) {
            consecutiveFailures++
            if (!isActive && consecutiveFailures >= threshold) {
                isActive = true
                nextProbeAt = nowMillis() + retryIntervalMs
                true
            } else {
                false
            }
        }
        if (entered) onChange(true)
    }

    /** Clears the failures and leaves fallback (e.g. profile or network change). */
    fun reset() {
        val left = synchronized(this) {
            consecutiveFailures = 0
            isActive.also { isActive = false }
        }
        if (left) onChange(false)
    }

    companion object {
        const val DEFAULT_THRESHOLD = 3
        const val DEFAULT_RETRY_INTERVAL_MS = 60_000L
    }
}
