package dev.downlevel.firedns.data

import kotlinx.serialization.Serializable

/** All persisted app state (a single DataStore JSON file). */
@Serializable
data class Settings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val activeProfileId: String = Presets.default.id,
    val vpnDesiredOn: Boolean = false,
    val autostartOnBoot: Boolean = true,
    val fallbackEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Daily check of the latest GitHub release (see update/UpdateChecker). */
    val checkUpdates: Boolean = true,
    /** Last versionCode whose "What's new" the user has seen; 0 = never recorded. */
    val lastSeenVersionCode: Int = 0,
    /** Last available versionCode already announced with a notification, so it is not repeated. */
    val notifiedUpdateVersionCode: Int = 0,
    val customProfiles: List<DnsProfile> = emptyList()
) {
    fun findProfile(id: String): DnsProfile? = Presets.byId(id) ?: customProfiles.firstOrNull { it.id == id }

    /** Active profile; if its id no longer exists, the default built-in profile. */
    fun activeProfile(): DnsProfile = findProfile(activeProfileId) ?: Presets.default

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
