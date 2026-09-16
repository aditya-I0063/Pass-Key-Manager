package com.bhardwaj.passkey

import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.domain.repository.PasskeyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory repository backed by StateFlows.
 *
 * A hand-written fake rather than a mock: these methods return flows that tests need to emit on,
 * and assertions about what was *not* written are clearer against real stored state than against
 * verify-never calls.
 */
class FakePasskeyRepository : PasskeyRepository {

    private val previews = MutableStateFlow<List<Preview>>(emptyList())
    private val details = MutableStateFlow<List<Detail>>(emptyList())
    private var nextId = 1L

    /** Counts writes so tests can assert nothing touched the database. */
    var writeCount = 0
        private set

    fun seedPreviews(vararg items: Preview) {
        previews.value = items.toList()
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
    }

    fun currentPreviews(): List<Preview> = previews.value
    fun currentDetails(): List<Detail> = details.value

    override fun getPreviews(): Flow<List<Preview>> = previews

    override fun getPreviewsByCategory(category: Category): Flow<List<Preview>> =
        previews.map { list -> list.filter { it.category == category }.sortedBy { it.sequence } }

    override suspend fun getPreviewById(previewId: Long): Preview? =
        previews.value.firstOrNull { it.id == previewId }

    override suspend fun getPreviewByHeading(heading: String, category: Category): Preview? =
        previews.value.firstOrNull {
            it.heading.equals(heading, ignoreCase = true) && it.category == category
        }

    override suspend fun createPreview(heading: String, category: Category, sequence: Long): Long {
        writeCount++
        val id = nextId++
        previews.value += Preview(id, heading, category, sequence)
        return id
    }

    override suspend fun updatePreview(preview: Preview) {
        writeCount++
        previews.value = previews.value.map { if (it.id == preview.id) preview else it }
    }

    override suspend fun deletePreview(preview: Preview) {
        writeCount++
        previews.value = previews.value.filterNot { it.id == preview.id }
    }

    override suspend fun updatePreviewSequence(previewId: Long, sequence: Long) {
        writeCount++
        previews.value = previews.value.map {
            if (it.id == previewId) it.copy(sequence = sequence) else it
        }
    }

    override fun getDetails(): Flow<List<Detail>> = details

    override fun getDetailsByPreviewId(previewId: Long): Flow<List<Detail>> =
        details.map { list -> list.filter { it.previewId == previewId }.sortedBy { it.sequence } }

    override suspend fun getDetailByContent(
        previewId: Long,
        question: String,
        answer: String
    ): Detail? = details.value.firstOrNull {
        it.previewId == previewId && it.question == question && it.answer == answer
    }

    override suspend fun createDetail(
        previewId: Long,
        question: String,
        answer: String,
        sequence: Long,
        isSecret: Boolean
    ): Long {
        writeCount++
        val id = nextId++
        details.value += Detail(id, previewId, question, answer, sequence, isSecret)
        return id
    }

    override suspend fun updateDetail(detail: Detail) {
        writeCount++
        details.value = details.value.map { if (it.id == detail.id) detail else it }
    }

    override suspend fun deleteDetail(detail: Detail) {
        writeCount++
        details.value = details.value.filterNot { it.id == detail.id }
    }

    override suspend fun deleteDetailsByPreviewId(previewId: Long) {
        writeCount++
        details.value = details.value.filterNot { it.previewId == previewId }
    }

    override suspend fun updateDetailSequence(detailId: Long, sequence: Long) {
        writeCount++
        details.value = details.value.map {
            if (it.id == detailId) it.copy(sequence = sequence) else it
        }
    }

    override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()

    override suspend fun deleteAll() {
        writeCount++
        previews.value = emptyList()
        details.value = emptyList()
    }
}
