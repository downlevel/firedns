package dev.downlevel.firedns.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackPolicyTest {

    private var now = 0L
    private val changes = mutableListOf<Boolean>()
    private val policy =
        FallbackPolicy(threshold = 3, retryIntervalMs = 1000, nowMillis = { now }, onChange = { changes += it })

    @Test
    fun `enters after the consecutive failure threshold`() {
        repeat(2) { policy.onPrimaryFailure() }
        assertFalse(policy.isActive)
        policy.onPrimaryFailure()
        assertTrue(policy.isActive)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `a success resets the count`() {
        repeat(2) { policy.onPrimaryFailure() }
        policy.onPrimarySuccess()
        repeat(2) { policy.onPrimaryFailure() }
        assertFalse(policy.isActive)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `a single probe per interval`() {
        repeat(3) { policy.onPrimaryFailure() }
        assertFalse(policy.shouldTryPrimary())
        now = 999
        assertFalse(policy.shouldTryPrimary())
        now = 1000
        assertTrue(policy.shouldTryPrimary())
        assertFalse(policy.shouldTryPrimary()) // the probe already started
        now = 2000
        assertTrue(policy.shouldTryPrimary())
    }

    @Test
    fun `reset leaves fallback only once`() {
        repeat(3) { policy.onPrimaryFailure() }
        policy.reset()
        policy.reset()
        assertFalse(policy.isActive)
        assertTrue(policy.shouldTryPrimary())
        assertEquals(listOf(true, false), changes)
    }
}
