package dev.downlevel.firedns.dns.resolver

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** DNS-over-HTTPS (RFC 8484) with POST `application/dns-message`. */
class DohResolver(private val url: HttpUrl, private val client: OkHttpClient) : DnsResolver {

    override suspend fun resolve(query: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", MEDIA_TYPE)
            .post(query.toRequestBody(MEDIA_TYPE.toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("DoH HTTP ${response.code}")
            val body = response.body?.bytes() ?: throw IOException("DoH response without body")
            if (body.size < 12) throw IOException("DoH response too short")
            body
        }
    }

    companion object {
        const val MEDIA_TYPE = "application/dns-message"
    }
}
