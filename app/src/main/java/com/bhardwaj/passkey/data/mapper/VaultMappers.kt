package com.bhardwaj.passkey.data.mapper

import com.bhardwaj.passkey.data.local.entity.DetailHistoryEntity
import com.bhardwaj.passkey.data.local.entity.DetailsEntity
import com.bhardwaj.passkey.data.local.entity.PreviewEntity
import com.bhardwaj.passkey.data.local.entity.TotpEntity
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.PasswordHistoryEntry
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.domain.model.TotpEntry
import com.bhardwaj.passkey.domain.totp.Base32
import com.bhardwaj.passkey.domain.totp.TotpAlgorithm
import com.bhardwaj.passkey.domain.totp.TotpConfig

internal fun PreviewEntity.toDomain(): Preview = Preview(
    // Safe by construction: Room always populates the primary key on a read. A row without one
    // would mean the database is corrupt, which is worth failing loudly on.
    id = requireNotNull(previewId) { "preview read from the database without an id" },
    heading = heading,
    category = categoryName,
    sequence = sequence
)

internal fun Preview.toEntity(): PreviewEntity = PreviewEntity(
    previewId = id.takeIf { it != 0L },
    heading = heading,
    categoryName = category,
    sequence = sequence
)

internal fun DetailsEntity.toDomain(): Detail = Detail(
    id = requireNotNull(detailsId) { "detail read from the database without an id" },
    previewId = previewId,
    question = question,
    answer = answer,
    sequence = sequence,
    isSecret = isSecret
)

internal fun Detail.toEntity(): DetailsEntity = DetailsEntity(
    detailsId = id.takeIf { it != 0L },
    previewId = previewId,
    question = question,
    answer = answer,
    sequence = sequence,
    isSecret = isSecret
)

/**
 * Returns null for a row whose secret is not valid Base32.
 *
 * That should be unreachable - the secret is validated before it is stored - but a code
 * generated from a half-decoded secret would be silently wrong, which is worse than an
 * authenticator that visibly is not there.
 */
internal fun TotpEntity.toDomain(): TotpEntry? {
    val decoded = Base32.decode(secret) ?: return null
    return TotpEntry(
        id = requireNotNull(totpId) { "authenticator read from the database without an id" },
        previewId = previewId,
        label = label,
        config = TotpConfig(
            secret = decoded,
            issuer = issuer,
            account = label,
            algorithm = TotpAlgorithm.fromNameOrDefault(algorithm),
            digits = digits,
            periodSeconds = periodSeconds
        )
    )
}

internal fun TotpEntry.toEntity(): TotpEntity = TotpEntity(
    totpId = id.takeIf { it != 0L },
    previewId = previewId,
    label = label,
    secret = Base32.encode(config.secret),
    issuer = config.issuer,
    algorithm = config.algorithm.name,
    digits = config.digits,
    periodSeconds = config.periodSeconds
)

internal fun DetailHistoryEntity.toDomain(): PasswordHistoryEntry = PasswordHistoryEntry(
    id = requireNotNull(historyId) { "history row read from the database without an id" },
    detailId = detailsId,
    answer = answer,
    changedAt = changedAt
)
