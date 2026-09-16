package com.bhardwaj.passkey

import com.bhardwaj.passkey.data.backup.LegacyCsvBackup
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The legacy reader has to cope with files the old exporter itself produced, including the
 * shapes it got wrong.
 */
class LegacyCsvBackupTest {

    private val header = "category,heading,question,answer\n"

    @Test
    fun `parses rows and groups details under their entry`() {
        val result = LegacyCsvBackup.parse(
            header +
                "BANKS,My Bank,Username,alice\n" +
                "BANKS,My Bank,Password,s3cret\n" +
                "MAILS,Personal Mail,Password,other\n"
        )
        assertThat(result.previews).hasSize(2)
        assertThat(result.skippedRows).isEqualTo(0)

        val bank = result.previews.first { it.heading == "My Bank" }
        assertThat(bank.categoryName).isEqualTo("BANKS")
        assertThat(bank.details.map { it.question }).containsExactly("Username", "Password")
    }

    @Test
    fun `un-escapes the comma sentinel the old exporter wrote`() {
        val result = LegacyCsvBackup.parse(header + "BANKS,My____Bank,Note,a____b\n")
        assertThat(result.previews.single().heading).isEqualTo("My,Bank")
        assertThat(result.previews.single().details.single().answer).isEqualTo("a,b")
    }

    @Test
    fun `rows with the wrong column count are skipped, not fatal`() {
        // The old importer let a malformed row abort the entire remaining file.
        val result = LegacyCsvBackup.parse(
            header +
                "BANKS,Good,Question,answer\n" +
                "BANKS,Broken,only-three\n" +
                "MAILS,Also Good,Question,answer\n"
        )
        assertThat(result.previews.map { it.heading }).containsExactly("Good", "Also Good")
        assertThat(result.skippedRows).isEqualTo(1)
    }

    @Test
    fun `an unknown category does not throw`() {
        // Categories.valueOf would have thrown here.
        val result = LegacyCsvBackup.parse(header + "NOT_A_CATEGORY,Thing,Question,answer\n")
        assertThat(result.previews).hasSize(1)
        assertThat(result.previews.single().categoryName).isEqualTo("NOT_A_CATEGORY")
    }

    @Test
    fun `blank headings are skipped`() {
        val result = LegacyCsvBackup.parse(header + "BANKS,,Question,answer\n")
        assertThat(result.previews).isEmpty()
        assertThat(result.skippedRows).isEqualTo(1)
    }

    @Test
    fun `empty file yields nothing`() {
        assertThat(LegacyCsvBackup.parse(header).previews).isEmpty()
        assertThat(LegacyCsvBackup.parse("").previews).isEmpty()
    }

    @Test
    fun `quoted values containing commas are read by the CSV parser`() {
        // opencsv understands RFC-4180 quoting, which the old hand-rolled split(",") did not.
        val result = LegacyCsvBackup.parse(header + "BANKS,\"Bank, National\",Question,answer\n")
        assertThat(result.previews.single().heading).isEqualTo("Bank, National")
    }
}
