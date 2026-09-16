package com.bhardwaj.passkey.data.security

import kotlinx.serialization.Serializable

enum class SlotId {
    /** Per-use strong biometric. Invalidated when biometric enrollment changes. */
    BIO,

    /** Device credential, time-bound. Survives biometric enrollment changes. */
    CRED,

    /** Recovery password. Survives even the device lock being removed entirely. */
    REC
}

enum class MigrationState {
    /** No vault has been provisioned yet. */
    NONE,

    /** Key slots are written but the database has not finished being re-keyed. */
    PENDING,

    DONE
}

@Serializable
sealed interface KeySlot {
    val id: SlotId

    /** Base64 GCM IV, then the wrapped data encryption key. */
    val ivB64: String
    val wrappedKeyB64: String

    @Serializable
    data class Keystore(
        override val id: SlotId,
        val alias: String,
        override val ivB64: String,
        override val wrappedKeyB64: String
    ) : KeySlot

    @Serializable
    data class Password(
        override val id: SlotId,
        val kdf: KdfSpec,
        override val ivB64: String,
        override val wrappedKeyB64: String
    ) : KeySlot
}

/**
 * The on-disk key slot file.
 *
 * Every slot wraps the *same* data encryption key, so unlocking needs any one of them. That is
 * what makes losing a fingerprint enrollment survivable without ever creating an unauthenticated
 * path to the key.
 *
 * [dekCheck] is an HMAC-style digest of the DEK. GCM already authenticates each unwrap, so this
 * is belt-and-braces, but it also gives the re-key a cheap post-condition: after unwrapping
 * through a different slot we can confirm we got the same key before touching the database.
 */
@Serializable
data class KeySlotFile(
    val version: Int = CURRENT_VERSION,
    val migrationState: MigrationState,
    val dekCheck: String,
    val slots: List<KeySlot>
) {
    init {
        // Invariants worth crashing over rather than silently persisting: a slot file with no
        // recovery slot, or none at all, is an unopenable vault.
        require(slots.isNotEmpty()) { "key slot file must contain at least one slot" }
        require(slots.any { it.id == SlotId.REC }) {
            "the recovery slot may never be absent: it is the only one that survives the device " +
                "lock being removed"
        }
        require(slots.map { it.id }.distinct().size == slots.size) { "duplicate slot ids" }
    }

    fun slot(id: SlotId): KeySlot? = slots.firstOrNull { it.id == id }

    fun withSlot(slot: KeySlot): KeySlotFile =
        copy(slots = slots.filterNot { it.id == slot.id } + slot)

    fun withoutSlot(id: SlotId): KeySlotFile {
        require(id != SlotId.REC) { "the recovery slot may not be removed" }
        return copy(slots = slots.filterNot { it.id == id })
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
