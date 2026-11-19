package dev.downlevel.firedns.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpdateCheckerTest {

    private val server = MockWebServer().apply { start() }
    private val checker = UpdateChecker(OkHttpClient(), server.url("/version.json").toString())
    private val dir: File = Files.createTempDirectory("updates").toFile()

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifest(sha256: String) = UpdateManifest(
        versionCode = 12,
        versionName = "1.1.0",
        apkUrl = server.url("/firedns.apk").toString(),
        sha256 = sha256
    )

    @Test
    fun `fetches and parses version json`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"versionCode":12,"versionName":"1.1.0","apkUrl":"https://x","sha256":"${"b".repeat(64)}"}"""
            )
        )
        assertEquals(12, checker.fetchLatest()?.versionCode)
        assertEquals("/version.json", server.takeRequest().path)
    }

    @Test
    fun `http errors and broken files give null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(checker.fetchLatest())
        server.enqueue(MockResponse().setBody("<html>"))
        assertNull(checker.fetchLatest())
    }

    @Test
    fun `download with matching sha256`() = runTest {
        val apk = ByteArray(200_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(apk)))
        val dest = File(dir, "firedns.apk")
        var lastProgress = 0f

        checker.download(manifest(sha256(apk)), dest) { lastProgress = it }

        assertArrayEquals(apk, dest.readBytes())
        assertEquals(1f, lastProgress)
        assertFalse(File(dir, "firedns.apk.part").exists())
    }

    @Test
    fun `sha256 mismatch deletes the file`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(1_000))))
        val dest = File(dir, "firedns.apk")
        try {
            checker.download(manifest("0".repeat(64)), dest) {}
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("SHA-256"))
        }
        assertFalse(dest.exists())
        assertFalse(File(dir, "firedns.apk.part").exists())
    }
}
