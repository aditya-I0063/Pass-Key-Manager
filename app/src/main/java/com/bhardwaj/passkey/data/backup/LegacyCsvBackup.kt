package com.bhardwaj.passkey.data.backup

import com.opencsv.CSVReader
import java.io.StringReader

/**
 * Read-only reader for the pre-5.6 plaintext `.passkey` CSV export.
 *
 * That format is retired - it wrote every credential in cleartext to public Downloads - but
 * users have those files, so restore has to keep working.
 *
 * Two defects in the old writer that this reader has to tolerate:
 *  - commas were escaped by substituting the literal `____`, which is lossy: a value genuinely
 *    containing `____` round-trips as a comma. Nothing can recover that, but un-escaping is
 *    still what the file means.
 *  - values containing a newline broke the line-based format outright; such rows are dropped
 *    rather than aborting the whole import, and counted so the UI can report them.
 */
internal object LegacyCsvBackup {

    private const val COMMA_SENTINEL = "____"
    private const val EXPECTED_COLUMNS = 4

    data class Result(val previews: List<BackupPreview>, val skippedRows: Int)

    fun parse(content: String): Result {
        val grouped = LinkedHashMap<Pair<String, String>, MutableList<BackupDetail>>()
        var skipped = 0

        val rows: List<Array<String>> = runCatching {
            CSVReader(StringReader(content)).use { it.readAll() }
        }.getOrElse {
            // Fall back to the naive split the old exporter itself used.
            content.lineSequence().map { line -> line.split(",").toTypedArray() }.toList()
        }

        rows.forEachIndexed { index, row ->
            if (index == 0) return@forEachIndexed          // header line
            if (row.size != EXPECTED_COLUMNS) {
                if (row.any { it.isNotBlank() }) skipped++
                return@forEachIndexed
            }
            val category = row[0].trim().unescape()
            val heading = row[1].trim().unescape()
            val question = row[2].trim().unescape()
            val answer = row[3].trim().unescape()

            if (heading.isBlank() || question.isBlank()) {
                skipped++
                return@forEachIndexed
            }
            grouped.getOrPut(heading to category) { mutableListOf() }
                .add(BackupDetail(question = question, answer = answer))
        }

        val previews = grouped.entries.mapIndexed { index, (key, details) ->
            BackupPreview(
                heading = key.first,
                categoryName = key.second,
                sequence = index.toLong(),
                details = details.mapIndexed { i, d -> d.copy(sequence = i.toLong()) }
            )
        }
        return Result(previews = previews, skippedRows = skipped)
    }

    private fun String.unescape(): String = replace(COMMA_SENTINEL, ",")
}
