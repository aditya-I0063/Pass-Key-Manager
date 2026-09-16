package com.bhardwaj.passkey.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [KeySlotFile] to `filesDir/vault/keyslots.json`.
 *
 * Deliberately not DataStore Preferences: that file is a plaintext proto inside the app's
 * shared-preferences backup domain. The wrapped key is safe at rest either way, but keeping it
 * in a dedicated file makes the backup exclusions unambiguous.
 *
 * Writes go to a temp file, are fsynced and then renamed, so a crash mid-write cannot leave a
 * truncated slot file — which would be an unopenable vault.
 */
@Singleton
class KeySlotStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    private val directory: File get() = File(context.filesDir, "vault")
    private val file: File get() = File(directory, "keyslots.json")
    private val tempFile: File get() = File(directory, "keyslots.json.tmp")

    /** Synchronous on purpose: the cold-start branch needs it before anything else runs. */
    fun exists(): Boolean = file.exists()

    suspend fun load(): KeySlotFile? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<KeySlotFile>(file.readText()) }.getOrNull()
    }

    suspend fun save(slotFile: KeySlotFile) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val encoded = json.encodeToString(slotFile)
        FileOutputStream(tempFile).use { out ->
            out.write(encoded.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        check(tempFile.renameTo(file)) { "could not replace key slot file" }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        tempFile.delete()
        Unit
    }
}
