package dev.downlevel.firedns.data

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ProfileRepository(private val store: DataStore<Settings>) {

    /** Built-in profiles followed by custom ones, in Home screen order. */
    val profiles: Flow<List<DnsProfile>> =
        store.data.map { Presets.all + it.customProfiles }.distinctUntilChanged()

    val activeProfile: Flow<DnsProfile> =
        store.data.map { it.activeProfile() }.distinctUntilChanged()

    /** Ignores unknown ids. */
    suspend fun setActive(id: String) {
        store.updateData { s -> if (s.findProfile(id) == null) s else s.copy(activeProfileId = id) }
    }

    /** Inserts a custom profile, or replaces it keeping its position. */
    suspend fun saveCustom(profile: DnsProfile) {
        require(!profile.isPreset) { "Built-in profiles cannot be modified" }
        store.updateData { s ->
            val index = s.customProfiles.indexOfFirst { it.id == profile.id }
            val updated = if (index < 0) {
                s.customProfiles + profile
            } else {
                s.customProfiles.toMutableList().also { it[index] = profile }
            }
            s.copy(customProfiles = updated)
        }
    }

    /** Deletes a custom profile; if it was active, falls back to the default. */
    suspend fun deleteCustom(id: String) {
        store.updateData { s ->
            s.copy(
                customProfiles = s.customProfiles.filterNot { it.id == id },
                activeProfileId = if (s.activeProfileId == id) Presets.default.id else s.activeProfileId
            )
        }
    }
}
