package com.coffeeledger.app.domain.importer

import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.domain.parse.SmsDateParser
import java.time.ZoneId

/**
 * Turns the flat text of a statement PDF into ledger rows.
 *
 * Statement layouts vary, so direction is decided by the strongest signal available, in
 * order: an explicit Dr/Cr marker, then the direction the running balance moved, then
 * separate debit/credit columns inferred from position. Where nothing is conclusive the
 * row is still imported as a debit and flagged in the report, because an import screen
 * that silently drops rows is worse than one that asks the user to check.
 */
object PdfStatementParser {

    private val LEADING_DATE = Regex(
        """^\s*(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{1,2}[-\s][A-Za-z]{3,4}[-\s,]?\d{2,4})""",
    )

    /** Statement figures almost always carry two decimals, which separates them from IDs. */
    private val DECIMAL_AMOUNT = Regex("""(?<![\d.])(\d{1,3}(?:,\d{2,3})*|\d+)\.\d{2}(?![\d])""")

    private val DR_CR = Regex("""\b(dr|cr)\b""", RegexOption.IGNORE_CASE)

    private val NOISE_LINES = listOf(
        "statement of account", "opening balance", "closing balance", "page ",
        "total", "brought forward", "carried forward", "customer id", "ifsc",
        "branch", "nominee", "this is a computer generated",
    )

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): ImportReport {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) {
            throw ImportException(
                "No text could be read from that PDF. Scanned statements are not supported — " +
                    "export a CSV from your bank instead.",
            )
        }

        val rows = mutableListOf<ImportedRow>()
        var skipped = 0
        var previousBalance: Long? = null
        var ambiguous = 0

        for (line in lines) {
            val lower = line.lowercase()
            if (NOISE_LINES.any { lower.startsWith(it) }) continue

            val dateMatch = LEADING_DATE.find(line)
            if (dateMatch == null) {
                skipped++
                continue
            }
            val date = SmsDateParser.findDate(dateMatch.value)
            if (date == null) {
                skipped++
                continue
            }

            val amounts = DECIMAL_AMOUNT.findAll(line).toList()
            if (amounts.isEmpty()) {
                skipped++
                continue
            }

            val hasBalanceColumn = amounts.size >= 2
            val amountMatch = if (hasBalanceColumn) amounts[amounts.size - 2] else amounts.last()
            val balance = if (hasBalanceColumn) Money.parseAmount(amounts.last().value) else null
            val amount = Money.parseAmount(amountMatch.value)
            if (amount == null || amount <= 0L) {
                skipped++
                continue
            }

            val marker = DR_CR.find(line)?.groupValues?.get(1)?.lowercase()
            val direction = when {
                marker == "cr" -> Direction.CREDIT
                marker == "dr" -> Direction.DEBIT
                balance != null && previousBalance != null -> {
                    if (balance >= previousBalance) Direction.CREDIT else Direction.DEBIT
                }
                else -> {
                    ambiguous++
                    Direction.DEBIT
                }
            }

            val description = line
                .substring(dateMatch.range.last + 1, amountMatch.range.first)
                .trim(' ', '|', '\t', '-')
                .ifEmpty { "Imported transaction" }

            rows.add(
                ImportedRow(
                    occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    description = description,
                    amountMinor = amount,
                    direction = direction,
                    balanceMinor = balance,
                ),
            )
            if (balance != null) previousBalance = balance
        }

        if (rows.isEmpty()) {
            throw ImportException(
                "No transaction rows were recognised in that PDF. If it is a scanned copy, " +
                    "export a CSV from your bank instead.",
            )
        }

        val notes = buildList {
            add("Imported rows are not saved until you review them.")
            if (skipped > 0) add("$skipped line(s) did not look like transactions and were ignored.")
            if (ambiguous > 0) {
                add("$ambiguous row(s) had no Dr/Cr marker or balance column and were read as debits. Check them.")
            }
        }
        return ImportReport(rows.sortedBy { it.occurredAt }, skipped, notes)
    }
}
