package com.coffeeledger.app.domain.importer

import com.coffeeledger.app.domain.model.Direction

/** One line lifted out of a statement, before it becomes a ledger entry. */
data class ImportedRow(
    val occurredAt: Long,
    val description: String,
    val amountMinor: Long,
    val direction: Direction,
    val reference: String? = null,
    val balanceMinor: Long? = null,
)

/** The outcome of reading a file: what was understood, and what was not. */
data class ImportReport(
    val rows: List<ImportedRow>,
    val skippedLines: Int,
    val notes: List<String> = emptyList(),
) {
    val importedCount: Int get() = rows.size
}

class ImportException(message: String) : Exception(message)
