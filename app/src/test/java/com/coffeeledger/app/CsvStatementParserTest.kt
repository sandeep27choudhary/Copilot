package com.coffeeledger.app

import com.coffeeledger.app.domain.importer.CsvStatementParser
import com.coffeeledger.app.domain.importer.ImportException
import com.coffeeledger.app.domain.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvStatementParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    @Test
    fun `reads separate debit and credit columns`() {
        val csv = """
            Date,Narration,Chq/Ref No,Withdrawal Amt,Deposit Amt,Closing Balance
            01-08-2026,NEFT SALARY ACME TECHNOLOGIES,NEFT26080,,125000.00,225000.00
            02-08-2026,UPI-BLINKIT COMMERCE PVT LTD,552312345678,742.00,,224258.00
            07-08-2026,REFUND AMAZON SELLER SERVICES,887766554433,,1499.00,225757.00
        """.trimIndent()

        val report = CsvStatementParser.parse(csv, zone)
        assertEquals(3, report.importedCount)
        assertEquals(Direction.CREDIT, report.rows[0].direction)
        assertEquals(1_25_000_00L, report.rows[0].amountMinor)
        assertEquals(Direction.DEBIT, report.rows[1].direction)
        assertEquals(74_200L, report.rows[1].amountMinor)
        assertEquals("552312345678", report.rows[1].reference)
        assertEquals(2_24_258_00L, report.rows[1].balanceMinor)
    }

    @Test
    fun `reads a single amount column with a type marker`() {
        val csv = """
            Txn Date,Description,Amount,Dr/Cr
            "05 Aug 2026","SWIGGY BUNDL TECHNOLOGIES","322.00","DR"
            "06 Aug 2026","INTEREST CREDIT","196.00","CR"
        """.trimIndent()

        val report = CsvStatementParser.parse(csv, zone)
        assertEquals(2, report.importedCount)
        assertEquals(Direction.DEBIT, report.rows[0].direction)
        assertEquals(Direction.CREDIT, report.rows[1].direction)
        assertEquals(19_600L, report.rows[1].amountMinor)
    }

    @Test
    fun `reads a signed amount column`() {
        val csv = """
            Date,Details,Amount
            10/08/2026,UBER INDIA SYSTEMS,-268.00
            11/08/2026,SALARY,+125000.00
        """.trimIndent()

        val report = CsvStatementParser.parse(csv, zone)
        assertEquals(Direction.DEBIT, report.rows[0].direction)
        assertEquals(Direction.CREDIT, report.rows[1].direction)
    }

    @Test
    fun `handles quoted fields containing commas`() {
        val cells = CsvStatementParser.splitCsvLine("""01-08-2026,"BLINKIT, BENGALURU",742.00""")
        assertEquals(listOf("01-08-2026", "BLINKIT, BENGALURU", "742.00"), cells)
    }

    @Test
    fun `skips junk rows and reports them`() {
        val csv = """
            Date,Narration,Withdrawal Amt,Deposit Amt
            01-08-2026,SALARY,,125000.00
            ,OPENING BALANCE,,
            not-a-date,GARBAGE,,
        """.trimIndent()

        val report = CsvStatementParser.parse(csv, zone)
        assertEquals(1, report.importedCount)
        assertEquals(2, report.skippedLines)
        assertTrue(report.notes.any { it.contains("skipped") })
    }

    @Test
    fun `rejects a file with no recognisable header`() {
        val error = assertThrows(ImportException::class.java) {
            CsvStatementParser.parse("hello,world\n1,2", zone)
        }
        assertTrue(error.message!!.contains("header"))
    }
}
