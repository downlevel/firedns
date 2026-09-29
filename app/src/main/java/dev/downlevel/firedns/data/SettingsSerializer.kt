package dev.downlevel.firedns.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object SettingsSerializer : Serializer<Settings> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(input: InputStream): Settings = try {
        json.decodeFromString(Settings.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable FireDNS settings", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable FireDNS settings", e)
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(json.encodeToString(Settings.serializer(), t).encodeToByteArray())
    }
}
