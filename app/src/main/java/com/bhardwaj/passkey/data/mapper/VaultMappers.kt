package com.bhardwaj.passkey.data.mapper

import com.bhardwaj.passkey.data.local.entity.DetailsEntity
import com.bhardwaj.passkey.data.local.entity.PreviewEntity
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.Preview

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
