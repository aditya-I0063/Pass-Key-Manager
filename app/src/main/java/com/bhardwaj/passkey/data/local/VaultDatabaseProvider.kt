package com.bhardwaj.passkey.data.local

import android.app.Application
import androidx.room.Room
import com.bhardwaj.passkey.utils.Constants
import com.bhardwaj.passkey.utils.MIGRATION_1_2
import com.bhardwaj.passkey.utils.MIGRATION_2_3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the encrypted database once a key is available, and closes it on lock.
 *
 * Previously the database was built inside a `@Singleton @Provides`, so any ViewModel injecting
 * the repository forced it open at construction time - before any authentication had happened.
 * That is incompatible with a key that only exists after a biometric unlock.
 */
@Singleton
class VaultDatabaseProvider @Inject constructor(
    private val application: Application
) {
    private val mutex = Mutex()

    private val _database = MutableStateFlow<PassKeyDatabase?>(null)
    val database: StateFlow<PassKeyDatabase?> = _database.asStateFlow()

    /** Kept so it can be zeroed on lock; see [closeAndWipe]. */
    private var keyBytes: ByteArray? = null

    val isOpen: Boolean get() = _database.value != null

    suspend fun open(dek: ByteArray): PassKeyDatabase = mutex.withLock {
        _database.value?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val key = dek.toSqlCipherRawKey()
            val db = Room.databaseBuilder(
                context = application,
                klass = PassKeyDatabase::class.java,
                name = Constants.PASS_KEY_DATABASE
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .openHelperFactory(
                    // clearPassphrase = false: the default zeroes the array after the first
                    // open, and Room may reopen the helper (after close, or a migration), which
                    // would then fail. We own the lifetime of these bytes instead.
                    SupportOpenHelperFactory(key, null, false)
                )
                .build()
            keyBytes = key
            _database.value = db
            db
        }
    }

    /** Suspends until the vault is unlocked. */
    suspend fun requireDb(): PassKeyDatabase = _database.filterNotNull().first()

    suspend fun closeAndWipe() = mutex.withLock {
        withContext(Dispatchers.IO) {
            _database.value?.let { runCatching { it.close() } }
            _database.value = null
            keyBytes?.let { Arrays.fill(it, 0) }
            keyBytes = null
        }
    }
}

/**
 * SQLCipher raw-key syntax: `x'<64 hex chars>'`.
 *
 * Passing the key as a passphrase would make SQLCipher run its own PBKDF2 - 256,000 iterations
 * by default in v4 - on every open. The DEK is already 256 bits of entropy from SecureRandom, so
 * that work buys nothing and would put a visible stall behind every unlock.
 */
internal fun ByteArray.toSqlCipherRawKey(): ByteArray =
    ("x'" + joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)
