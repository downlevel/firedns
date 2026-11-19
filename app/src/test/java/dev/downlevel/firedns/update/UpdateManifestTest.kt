package dev.downlevel.firedns.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val sha = "a".repeat(64)

    private fun json(versionCode: Int = 12, sha256: String = sha, extra: String = "") = """
        {"versionCode":$versionCode,"versionName":"1.1.0",
         "apkUrl":"https://github.com/downlevel/firedns/releases/download/v1.1.0/firedns.apk",
         "sha256":"$sha256","notes":{"en":["One"],"it":["Uno"]}$extra}
    """.trimIndent()

    @Test
    fun `parses the file written by release yml`() {
        val manifest = UpdateManifest.parse(json())
        assertNotNull(manifest)
        assertEquals(12, manifest!!.versionCode)
        assertEquals("1.1.0", manifest.versionName)
        assertTrue(manifest.hasTrustedApkUrl())
        assertEquals(listOf("Uno"), manifest.notesFor("it"))
    }

    @Test
    fun `unknown fields are ignored`() {
        assertNotNull(UpdateManifest.parse(json(extra = ""","minSdk":28""")))
    }

    @Test
    fun `invalid or incomplete files are rejected`() {
        assertNull(UpdateManifest.parse("{not json"))
        assertNull(UpdateManifest.parse("""{"versionName":"1.1.0"}"""))
        assertNull(UpdateManifest.parse(json(versionCode = 0)))
        assertNull(UpdateManifest.parse(json(sha256 = "abc")))
    }

    @Test
    fun `newer only with a higher versionCode`() {
        val manifest = UpdateManifest.parse(json(versionCode = 12))!!
        assertTrue(manifest.isNewerThan(11))
        assertFalse(manifest.isNewerThan(12))
        assertFalse(manifest.isNewerThan(13))
    }

    @Test
    fun `apk must come from this repository's releases`() {
        val manifest = UpdateManifest.parse(json())!!
        assertFalse(manifest.copy(apkUrl = "https://example.com/firedns.apk").hasTrustedApkUrl())
        assertFalse(
            manifest.copy(apkUrl = "http://github.com/downlevel/firedns/releases/download/x").hasTrustedApkUrl()
        )
        assertFalse(
            manifest.copy(apkUrl = "https://github.com/someone/firedns/releases/download/v1/firedns.apk")
                .hasTrustedApkUrl()
        )
    }

    @Test
    fun `notes fall back to English, then to any language`() {
        val notes = mapOf("en" to listOf("One"), "it" to listOf("Uno"))
        assertEquals(listOf("Uno"), ReleaseNotes.pick(notes, "it"))
        assertEquals(listOf("One"), ReleaseNotes.pick(notes, "de"))
        assertEquals(listOf("Eins"), ReleaseNotes.pick(mapOf("de" to listOf("Eins")), "fr"))
        assertEquals(listOf("One"), ReleaseNotes.pick(mapOf("it" to emptyList(), "en" to listOf("One")), "it"))
        assertTrue(ReleaseNotes.pick(emptyMap(), "en").isEmpty())
    }

    @Test
    fun `release notes asset parsing`() {
        assertEquals(mapOf("en" to listOf("One")), ReleaseNotes.parse("""{"en":["One"]}"""))
        assertTrue(ReleaseNotes.parse("[]").isEmpty())
        assertTrue(ReleaseNotes.parse("{broken").isEmpty())
    }

    @Test
    fun `what's new only after an update`() {
        fun show(onboardingDone: Boolean, lastSeen: Int) = ReleaseNotes.shouldShowWhatsNew(onboardingDone, lastSeen, 12)
        // Fresh install: onboarding not done yet.
        assertFalse(show(onboardingDone = false, lastSeen = 0))
        // First version with the updater, installed over an older one.
        assertTrue(show(onboardingDone = true, lastSeen = 0))
        assertTrue(show(onboardingDone = true, lastSeen = 11))
        // Already seen.
        assertFalse(show(onboardingDone = true, lastSeen = 12))
    }
}
