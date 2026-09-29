package dev.downlevel.firedns.ui.editor

import dev.downlevel.firedns.data.DnsAddress
import dev.downlevel.firedns.data.DnsProfile

/** Editor form state, with no Android dependencies: testable on the JVM. */
data class EditorForm(val name: String = "", val address: String = "") {
    val parsedAddress: DnsAddress? get() = DnsAddress.parse(address)

    /** Error shown only after the user has typed something. */
    val showAddressError: Boolean get() = address.isNotBlank() && parsedAddress == null

    val canSave: Boolean get() = name.isNotBlank() && parsedAddress != null

    /** Profile to save; [id] `null` = new profile. `null` if the form is invalid. */
    fun toProfile(id: String?): DnsProfile? {
        val address = parsedAddress ?: return null
        if (name.isBlank()) return null
        return if (id == null) DnsProfile.custom(name.trim(), address) else DnsProfile.custom(name.trim(), address, id)
    }

    companion object {
        const val MAX_NAME_LENGTH = 32
    }
}
