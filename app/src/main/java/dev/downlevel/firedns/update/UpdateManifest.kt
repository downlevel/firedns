package dev.downlevel.firedns.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * `version.json`, published by release.yml next to `firedns.apk` in every GitHub Release.
 * [notes] maps a language code ("en", "it") to the bullet points of that release.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: Map<String, List<String>> = emptyMap()
) {
    fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode

    /** Only APKs from this repository's releases, never an arbitrary URL. */
    fun hasTrustedApkUrl(): Boolean = apkUrl.startsWith(TRUSTED_APK_PREFIX)

    fun notesFor(language: String): List<String> = ReleaseNotes.pick(notes, language)

    companion object {
        const val URL = "https://github.com/downlevel/firedns/releases/latest/download/version.json"
        const val TRUSTED_APK_PREFIX = "https://github.com/downlevel/firedns/releases/download/"

        private val json = Json { ignoreUnknownKeys = true }

        /** `null` if the file is unreadable or incomplete. */
        fun parse(text: String): UpdateManifest? = try {
            json.decodeFromString(serializer(), text).takeIf {
                it.versionCode > 0 && it.versionName.isNotBlank() && it.sha256.length == 64
            }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

object ReleaseNotes {
    private val json = Json { ignoreUnknownKeys = true }
    private val notesSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    /** The user's language if present, otherwise English, otherwise whatever exists. */
    fun pick(notes: Map<String, List<String>>, language: String): List<String> =
        notes[language]?.takeIf { it.isNotEmpty() }
            ?: notes["en"]?.takeIf { it.isNotEmpty() }
            ?: notes.values.firstOrNull { it.isNotEmpty() }
            ?: emptyList()

    /** Parses a `release-notes/<version>.json` file (`{"en": [...], "it": [...]}`). */
    fun parse(text: String): Map<String, List<String>> = try {
        json.decodeFromString(notesSerializer, text)
    } catch (e: SerializationException) {
        emptyMap()
    } catch (e: IllegalArgumentException) {
        emptyMap()
    }

    /**
     * "What's new" is shown once after an update, never after a fresh install
     * (onboarding not done yet) and never when the version has not changed.
     */
    fun shouldShowWhatsNew(onboardingDone: Boolean, lastSeenVersionCode: Int, installedVersionCode: Int): Boolean =
        onboardingDone && lastSeenVersionCode < installedVersionCode
}
