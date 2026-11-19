package dev.downlevel.firedns.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Network side of the updater. A single anonymous GET of a static file on GitHub:
 * no identifiers, no headers beyond OkHttp's defaults.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val manifestUrl: String = UpdateManifest.URL
) {

    /** Latest published release, or `null` on network errors or an unreadable file. */
    suspend fun fetchLatest(): UpdateManifest? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(manifestUrl).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                UpdateManifest.parse(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Downloads the APK into [dest] and checks its SHA-256. On mismatch the file is deleted
     * and [IOException] is thrown. [onProgress] receives 0..1, or -1 when the size is unknown.
     */
    suspend fun download(manifest: UpdateManifest, dest: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val partial = File(dest.parentFile, dest.name + ".part")
            client.newCall(Request.Builder().url(manifest.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            onProgress(if (total > 0) read.toFloat() / total else -1f)
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(manifest.sha256, ignoreCase = true)) {
                    partial.delete()
                    throw IOException("SHA-256 mismatch")
                }
            }
            dest.delete()
            if (!partial.renameTo(dest)) throw IOException("Cannot move the downloaded APK")
        }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
