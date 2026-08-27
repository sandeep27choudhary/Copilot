package com.coffeeledger.app

import com.coffeeledger.app.domain.importer.ImportException
import com.coffeeledger.app.domain.importer.PdfStatementParser
import com.coffeeledger.app.domain.importer.PdfTextExtractor
import com.coffeeledger.app.domain.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.Deflater

class PdfStatementTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private val statementLines = listOf(
        "Statement of Account - HDFC Bank",
        "Date Narration Amount Balance",
        "01-08-2026 NEFT CR ACME TECHNOLOGIES PAYROLL 125000.00 225000.00",
        "02-08-2026 UPI BLINKIT COMMERCE PVT LTD 742.00 224258.00",
        "03-08-2026 NACH NETFLIX SUBSCRIPTION 649.00 223609.00",
        "05-08-2026 UPI ZOMATO LIMITED 486.00 223123.00",
        "07-08-2026 REFUND AMAZON SELLER SERVICES 1499.00 224622.00",
    )

    // ------------------------------------------------------- extraction

    @Test
    fun `extracts text from an uncompressed pdf`() {
        val text = PdfTextExtractor.extract(buildPdf(statementLines, compress = false))
        statementLines.forEach { assertTrue("missing: $it", text.contains(it)) }
    }

    @Test
    fun `extracts text from a flate compressed pdf`() {
        val text = PdfTextExtractor.extract(buildPdf(statementLines, compress = true))
        assertTrue(text.contains("BLINKIT COMMERCE PVT LTD"))
        assertTrue(text.contains("125000.00"))
    }

    @Test
    fun `reads text from a TJ array with kerning`() {
        val content = "BT /F1 10 Tf 50 700 Td [(Date) -400 (Narration) -400 (Amount)] TJ ET"
        assertEquals("Date Narration Amount", PdfTextExtractor.renderContentStream(content).trim())
    }

    @Test
    fun `decodes escaped and octal characters in literal strings`() {
        val content = """BT 50 700 Td (A\(B\) C\055D) Tj ET"""
        assertEquals("A(B) C-D", PdfTextExtractor.renderContentStream(content).trim())
    }

    @Test
    fun `refuses a file that is not a pdf`() {
        val error = assertThrows(ImportException::class.java) {
            PdfTextExtractor.extract("just some text".toByteArray())
        }
        assertTrue(error.message!!.contains("not a PDF"))
    }

    @Test
    fun `refuses a password protected pdf`() {
        val bytes = ("%PDF-1.4\ntrailer<</Root 1 0 R/Encrypt 9 0 R>>\n%%EOF").toByteArray(Charsets.ISO_8859_1)
        val error = assertThrows(ImportException::class.java) { PdfTextExtractor.extract(bytes) }
        assertTrue(error.message!!.contains("password protected"))
    }

    // ----------------------------------------------------------- rows

    @Test
    fun `turns statement text into ledger rows`() {
        val text = PdfTextExtractor.extract(buildPdf(statementLines, compress = true))
        val report = PdfStatementParser.parse(text, zone)

        assertEquals(5, report.importedCount)
        val salary = report.rows.first()
        assertEquals(Direction.CREDIT, salary.direction)
        assertEquals(1_25_000_00L, salary.amountMinor)
        assertTrue(salary.description.contains("ACME"))

        val blinkit = report.rows[1]
        assertEquals(Direction.DEBIT, blinkit.direction)
        assertEquals(74_200L, blinkit.amountMinor)
        assertEquals(2_24_258_00L, blinkit.balanceMinor)

        val refund = report.rows.last()
        assertEquals(Direction.CREDIT, refund.direction)
    }

    @Test
    fun `explains itself when no rows are recognised`() {
        val error = assertThrows(ImportException::class.java) {
            PdfStatementParser.parse("Some cover page text with no transactions", zone)
        }
        assertTrue(error.message!!.contains("scanned"))
    }

    @Test
    fun `reports that imported rows still need review`() {
        val text = PdfTextExtractor.extract(buildPdf(statementLines, compress = false))
        val report = PdfStatementParser.parse(text, zone)
        assertTrue(report.notes.any { it.contains("review") })
    }

    // -------------------------------------------------------- fixtures

    /** Builds a minimal one-page PDF whose content stream draws [lines] one per row. */
    private fun buildPdf(lines: List<String>, compress: Boolean): ByteArray {
        val content = buildString {
            append("BT /F1 10 Tf 50 760 Td\n")
            lines.forEachIndexed { index, line ->
                if (index > 0) append("0 -16 Td\n")
                append("(").append(line.replace("(", "\\(").replace(")", "\\)")).append(") Tj\n")
            }
            append("ET\n")
        }.toByteArray(Charsets.ISO_8859_1)

        val streamBytes = if (compress) deflate(content) else content
        val filter = if (compress) "/Filter/FlateDecode" else ""

        val out = ByteArrayOutputStream()
        fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))

        write("%PDF-1.4\n")
        write("1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n")
        write("2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n")
        write("3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Contents 4 0 R>>endobj\n")
        write("4 0 obj<</Length ${streamBytes.size}$filter>>\nstream\n")
        out.write(streamBytes)
        write("\nendstream\nendobj\n")
        write("trailer<</Root 1 0 R>>\n%%EOF\n")
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }
}
