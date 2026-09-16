package com.bhardwaj.passkey.data.security

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bhardwaj.passkey.data.local.VaultDatabaseProvider
import com.bhardwaj.passkey.data.repository.DataStoreRepository
import com.bhardwaj.passkey.utils.SecureClipboard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-locks the vault when the app goes to the background for longer than the configured timeout.
 *
 * Registered against ProcessLifecycleOwner rather than an Activity, so a configuration change or
 * an internal activity transition does not count as leaving the app.
 */
@Singleton
class AppLockObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val session: VaultSession,
    private val vault: VaultDatabaseProvider,
    private val dataStoreRepository: DataStoreRepository
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStop(owner: LifecycleOwner) {
        session.backgroundedAtElapsed = android.os.SystemClock.elapsedRealtime()
        scope.launch {
            if (timeout().millis == 0L) lockNow(LockReason.Background)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val timeout = timeout()
            if (timeout == AutoLockTimeout.NEVER) return@launch
            val backgroundedAt = session.backgroundedAtElapsed
            if (backgroundedAt == 0L) return@launch
            val away = android.os.SystemClock.elapsedRealtime() - backgroundedAt
            if (away >= timeout.millis) lockNow(LockReason.Background)
        }
    }

    /** Called from the foreground idle poll in MainActivity. */
    fun lockIfIdle() {
        scope.launch {
            val timeout = timeout()
            if (timeout == AutoLockTimeout.NEVER) return@launch
            if (session.isUnlocked.value && session.idleMillis() >= timeout.millis) {
                lockNow(LockReason.Idle)
            }
        }
    }

    fun lockNow(reason: LockReason) {
        // Clear any copied secret first: once locked there is no UI left to offer a "clear" action.
        SecureClipboard.clearIfOurs(context)
        scope.launch {
            // Closing the database is the part that actually revokes access: it drops the
            // SQLCipher handle and zeroes the key bytes, so locking is no longer just a boolean.
            vault.closeAndWipe()
            session.lock(reason)
        }
    }

    suspend fun timeout(): AutoLockTimeout = AutoLockTimeout.fromMillis(
        dataStoreRepository.readPreference(
            key = DataStoreRepository.autoLockTimeoutKey,
            defaultValue = AutoLockTimeout.DEFAULT.millis
        ).first()
    )
}
