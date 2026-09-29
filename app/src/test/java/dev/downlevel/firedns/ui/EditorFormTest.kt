package dev.downlevel.firedns.ui

import dev.downlevel.firedns.data.DnsProtocol
import dev.downlevel.firedns.ui.editor.EditorForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFormTest {

    @Test
    fun `empty form cannot be saved and shows no error`() {
        val form = EditorForm()
        assertFalse(form.canSave)
        assertFalse(form.showAddressError)
        assertNull(form.toProfile(null))
    }

    @Test
    fun `address error only when not blank and invalid`() {
        assertTrue(EditorForm("Pi-hole", "192.168.1").showAddressError)
        assertFalse(EditorForm("Pi-hole", "   ").showAddressError)
        assertFalse(EditorForm("Pi-hole", "192.168.1.2").showAddressError)
    }

    @Test
    fun `name is required`() {
        assertFalse(EditorForm("  ", "9.9.9.9").canSave)
        assertNull(EditorForm("  ", "9.9.9.9").toProfile(null))
    }

    @Test
    fun `new profile with trimmed name`() {
        val profile = EditorForm("  NextDNS  ", "https://dns.nextdns.io/x").toProfile(null)!!
        assertEquals("NextDNS", profile.name)
        assertEquals(DnsProtocol.DOH, profile.protocol)
        assertFalse(profile.isPreset)
    }

    @Test
    fun `editing keeps the id`() {
        val profile = EditorForm("Pi-hole", "192.168.1.2").toProfile("c1")!!
        assertEquals("c1", profile.id)
        assertEquals(DnsProtocol.UDP, profile.protocol)
    }
}
