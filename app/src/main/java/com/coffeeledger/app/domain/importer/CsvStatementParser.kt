package com.coffeeledger.app.domain.importer

import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.money.Money
import com.coffeeledger.app.domain.parse.SmsDateParser
import java.time.ZoneId

/**
 * Reads a bank or card statement exported as CSV.
 *
 * Column names differ between every bank, so headers are matched by keyword rather than
 * by exact name, and both layouts are handled: separate debit/credit columns, or a single
 * amount column with a Dr/Cr marker or a sign.
 */
object CsvStatementParser {

    private val DATE_KEYS = listOf("date", "txn date", "transaction date", "value date", "posted")
    private val DESCRIPTION_KEYS = listOf("description", "narration", "particulars", "details", "remarks", "transaction", "merchant")
    private val DEBIT_KEYS = listOf("debit", "withdrawal", "withdrawal amt", "dr", "money out", "paid out")
    private val CREDIT_KEYS = listOf("credit", "deposit", "deposit amt", "cr", "money in", "paid in")
    private val AMOUNT_KEYS = listOf("amount", "amt", "transaction amount", "value")
    private val TYPE_KEYS = listOf("type", "dr/cr", "cr/dr", "direction", "txn type")
    private val REF_KEYS = listOf("ref", "reference", "chq", "cheque", "utr", "rrn", "transaction id")
    private val BALANCE_KEYS = listOf("balance", "closing balance", "running balance")

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): ImportReport {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) throw ImportException("The file is empty.")

        val headerIndex = lines.indexOfFirst { line ->
            val cells = splitCsvLine(line).map { it.lowercase() }
            cells.any { cell -> DATE_KEYS.any { cell.contains(it) } } &&
                cells.any { cell -> DESCRIPTION_KEYS.any { c -> cell.contains(c) } || AMOUNT_KEYS.any { c -> cell.contains(c) } }
        }
        if (headerIndex < 0) {
            throw ImportException("Could not find a header row with a date and an amount column.")
        }

        val header = splitCsvLine(lines[headerIndex]).map { it.trim().lowercase() }
        val dateCol = header.indexOfFirstMatching(DATE_KEYS)
        val descriptionCol = header.indexOfFirstMatching(DESCRIPTION_KEYS)
        val debitCol = header.indexOfFirstMatching(DEBIT_KEYS)
        val creditCol = header.indexOfFirstMatching(CREDIT_KEYS)
        val amountCol = header.indexOfFirstMatching(AMOUNT_KEYS)
        val typeCol = header.indexOfFirstMatching(TYPE_KEYS)
        val refCol = header.indexOfFirstMatching(REF_KEYS)
        val balanceCol = header.indexOfFirstMatching(BALANCE_KEYS)

        if (dateCol < 0) throw ImportException("No date column found.")
        if (debitCol < 0 && creditCol < 0 && amountCol < 0) {
            throw ImportException("No amount, debit or credit column found.")
        }

        val rows = mutableListOf<ImportedRow>()
        var skipped = 0
        for (line in lines.drop(headerIndex + 1)) {
            val cells = splitCsvLine(line)
            val row = buildRow(cells, dateCol, descriptionCol, debitCol, creditCol, amountCol, typeCol, refCol, balanceCol, zone)
            if (row == null) skipped++ else rows.add(row)
        }

        val notes = buildList {
            if (skipped > 0) add("$skipped line(s) had no readable date or amount and were skipped.")
            if (debitCol < 0 && creditCol < 0 && typeCol < 0) {
                add("Direction was taken from the sign of the amount column.")
            }
        }
        return ImportReport(rows.sortedBy { it.occurredAt }, skipped, notes)
    }

    private fun buildRow(
        cells: List<String>,
        dateCol: Int,
        descriptionCol: Int,
        debitCol: Int,
        creditCol: Int,
        amountCol: Int,
        typeCol: Int,
        refCol: Int,
        balanceCol: Int,
        zone: ZoneId,
    ): ImportedRow? {
        val dateText = cells.getOrNull(dateCol)?.trim().orEmpty()
        if (dateText.isEmpty()) return null
        val date = SmsDateParser.findDate(dateText) ?: return null
        val occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli()

        val debit = cells.getOrNull(debitCol)?.let { parseCell(it) }
        val credit = cells.getOrNull(creditCol)?.let { parseCell(it) }

        var amount: Long
        var direction: Direction
        when {
            debit != null && debit > 0L -> {
                amount = debit
                direction = Direction.DEBIT
            }
            credit != null && credit > 0L -> {
                amount = credit
                direction = Direction.CREDIT
            }
            else -> {
                val rawAmount = cells.getOrNull(amountCol)?.trim() ?: return null
                val magnitude = parseCell(rawAmount) ?: return null
                if (magnitude == 0L) return null
                amount = magnitude
                val typeText = cells.getOrNull(typeCol)?.trim()?.lowercase().orEmpty()
                direction = when {
                    typeText.startsWith("cr") || typeText.contains("credit") || typeText.contains("deposit") -> Direction.CREDIT
                    typeText.startsWith("dr") || typeText.contains("debit") || typeText.contains("withdraw") -> Direction.DEBIT
                    rawAmount.trimStart().startsWith("-") -> Direction.DEBIT
                    rawAmount.trimStart().startsWith("+") -> Direction.CREDIT
                    else -> Direction.DEBIT
                }
            }
        }
        if (amount <= 0L) return null

        return ImportedRow(
            occurredAt = occurredAt,
            description = cells.getOrNull(descriptionCol)?.trim().orEmpty().ifEmpty { "Imported transaction" },
            amountMinor = amount,
            direction = direction,
            reference = cells.getOrNull(refCol)?.trim()?.ifEmpty { null },
            balanceMinor = cells.getOrNull(balanceCol)?.let { parseCell(it) },
        )
    }

    private fun parseCell(raw: String): Long? {
        val cleaned = raw.trim()
            .removePrefix("+")
            .removePrefix("-")
            .replace("₹", "")
            .replace(Regex("""(?i)\b(inr|rs\.?)\b"""), "")
            .replace("(", "")
            .replace(")", "")
            .trim()
        if (cleaned.isEmpty() || cleaned == "-") return null
        return Money.parseAmount(cleaned)
    }

    private fun List<String>.indexOfFirstMatching(keys: List<String>): Int =
        indexOfFirst { cell -> keys.any { cell == it } }
            .takeIf { it >= 0 }
            ?: indexOfFirst { cell -> keys.any { cell.contains(it) } }

    /** A small RFC 4180 reader: handles quoted fields and doubled quotes inside them. */
    internal fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes && char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    cells.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells.add(current.toString().trim())
        return cells
    }
}
