package com.coffeeledger.app.data.io

import com.coffeeledger.app.data.repo.LedgerSnapshot
import com.coffeeledger.app.domain.model.Account
import com.coffeeledger.app.domain.model.AccountType
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.CategorySource
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.model.SourceType
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerPeriod
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.domain.money.Money
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A backup, read back into memory. */
data class BackupContents(
    val transactions: List<Txn>,
    val accounts: List<Account>,
    val trackers: List<Tracker>,
)

/**
 * Export and import of the user's own data.
 *
 * Both directions are plain files the user chooses a location for; the app never uploads
 * anything. A backup is portable and readable, which is the point: data you cannot get out
 * is data you do not really own.
 */
object DataTransfer {

    const val BACKUP_VERSION = 1
    const val JSON_MIME = "application/json"
    const val CSV_MIME = "text/csv"

    private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun backupFileName(now: Long): String =
        "coffee-ledger-backup-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}.json"

    fun csvFileName(now: Long): String =
        "coffee-ledger-transactions-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}.csv"

    // ----------------------------------------------------------- export

    fun toJson(snapshot: LedgerSnapshot, now: Long): String {
        val root = JSONObject()
        root.put("format", "coffee-ledger-backup")
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", now)
        root.put("note", "Exported from this device. Contains your financial data in the clear — store it somewhere safe.")

        root.put(
            "accounts",
            JSONArray().apply {
                snapshot.accounts.forEach { account ->
                    put(
                        JSONObject()
                            .put("id", account.id)
                            .put("displayName", account.displayName)
                            .put("institution", account.institution)
                            .put("tail", account.tail ?: JSONObject.NULL)
                            .put("type", account.type.name)
                            .put("openingBalanceMinor", account.openingBalanceMinor)
                            .put("includeInTotals", account.includeInTotals),
                    )
                }
            },
        )

        root.put(
            "trackers",
            JSONArray().apply {
                snapshot.trackers.forEach { tracker ->
                    put(
                        JSONObject()
                            .put("id", tracker.id)
                            .put("title", tracker.title)
                            .put("kind", tracker.kind.name)
                            .put("period", tracker.period.name)
                            .put("targetMinor", tracker.targetMinor)
                            .put("categoryIds", JSONArray(tracker.categoryIds))
                            .put("merchantNames", JSONArray(tracker.merchantNames))
                            .put("accountIds", JSONArray(tracker.accountIds))
                            .put("manualProgressMinor", tracker.manualProgressMinor)
                            .put("sortOrder", tracker.sortOrder)
                            .put("archived", tracker.archived),
                    )
                }
            },
        )

        root.put(
            "transactions",
            JSONArray().apply {
                snapshot.transactions.forEach { txn ->
                    put(
                        JSONObject()
                            .put("id", txn.id)
                            .put("occurredAt", txn.occurredAt)
                            .put("amountMinor", txn.amountMinor)
                            .put("direction", txn.direction.name)
                            .put("merchant", txn.merchant)
                            .put("merchantRaw", txn.merchantRaw)
                            .put("category", txn.category.id)
                            .put("categorySource", txn.categorySource.name)
                            .put("accountId", txn.accountId ?: JSONObject.NULL)
                            .put("accountTail", txn.accountTail ?: JSONObject.NULL)
                            .put("sourceType", txn.sourceType.name)
                            .put("sourceApp", txn.sourceApp)
                            .put("paymentMethod", txn.paymentMethod.name)
                            .put("reference", txn.reference ?: JSONObject.NULL)
                            .put("notes", txn.notes ?: JSONObject.NULL)
                            .put("isTransfer", txn.isTransfer),
                    )
                }
            },
        )
        return root.toString(2)
    }

    fun toCsv(transactions: List<Txn>, zone: ZoneId = ZoneId.systemDefault()): String {
        val builder = StringBuilder()
        builder.append("Date,Time,Amount,Direction,Merchant,Category,Account,Payment method,Source,Reference,Transfer,Notes\n")
        transactions.sortedByDescending { it.occurredAt }.forEach { txn ->
            val moment = Instant.ofEpochMilli(txn.occurredAt).atZone(zone)
            builder.append(moment.toLocalDate()).append(',')
            builder.append(moment.toLocalTime().withSecond(0).withNano(0)).append(',')
            builder.append(txn.amountMinor / Money.MINOR_PER_MAJOR.toDouble()).append(',')
            builder.append(txn.direction.name).append(',')
            builder.append(escape(txn.merchant)).append(',')
            builder.append(escape(txn.category.label)).append(',')
            builder.append(escape(txn.accountTail?.let { "****$it" } ?: "")).append(',')
            builder.append(escape(txn.paymentMethod.label)).append(',')
            builder.append(escape(txn.sourceApp)).append(',')
            builder.append(escape(txn.reference.orEmpty())).append(',')
            builder.append(if (txn.isTransfer) "yes" else "no").append(',')
            builder.append(escape(txn.notes.orEmpty())).append('\n')
        }
        return builder.toString()
    }

    // ----------------------------------------------------------- import

    fun fromJson(text: String): BackupContents {
        val root = JSONObject(text)
        require(root.optString("format") == "coffee-ledger-backup") {
            "That file is not a Coffee Ledger backup."
        }

        val accounts = root.optJSONArray("accounts").mapObjects { item ->
            Account(
                id = item.getString("id"),
                displayName = item.getString("displayName"),
                institution = item.optString("institution"),
                tail = item.optStringOrNull("tail"),
                type = enumOrDefault(item.optString("type"), AccountType.BANK),
                openingBalanceMinor = item.optLong("openingBalanceMinor"),
                includeInTotals = item.optBoolean("includeInTotals", true),
            )
        }

        val trackers = root.optJSONArray("trackers").mapObjects { item ->
            Tracker(
                id = item.getString("id"),
                title = item.getString("title"),
                kind = enumOrDefault(item.optString("kind"), TrackerKind.SPENDING_LIMIT),
                period = enumOrDefault(item.optString("period"), TrackerPeriod.MONTHLY),
                targetMinor = item.optLong("targetMinor"),
                categoryIds = item.optJSONArray("categoryIds").mapStrings(),
                merchantNames = item.optJSONArray("merchantNames").mapStrings(),
                accountIds = item.optJSONArray("accountIds").mapStrings(),
                manualProgressMinor = item.optLong("manualProgressMinor"),
                sortOrder = item.optInt("sortOrder"),
                archived = item.optBoolean("archived", false),
            )
        }

        val transactions = root.optJSONArray("transactions").mapObjects { item ->
            val direction = enumOrDefault(item.optString("direction"), Direction.DEBIT)
            Txn(
                id = item.getString("id"),
                occurredAt = item.getLong("occurredAt"),
                amountMinor = item.getLong("amountMinor"),
                direction = direction,
                merchant = item.optString("merchant", "Unknown"),
                merchantRaw = item.optString("merchantRaw", item.optString("merchant", "Unknown")),
                category = Category.fromId(item.optString("category")) ?: Category.defaultFor(direction),
                categorySource = enumOrDefault(item.optString("categorySource"), CategorySource.AUTO),
                accountId = item.optStringOrNull("accountId"),
                accountTail = item.optStringOrNull("accountTail"),
                sourceType = enumOrDefault(item.optString("sourceType"), SourceType.MANUAL),
                sourceApp = item.optString("sourceApp", "Imported"),
                paymentMethod = enumOrDefault(item.optString("paymentMethod"), PaymentMethod.UNKNOWN),
                reference = item.optStringOrNull("reference"),
                notes = item.optStringOrNull("notes"),
                isTransfer = item.optBoolean("isTransfer", false),
            )
        }

        return BackupContents(transactions, accounts, trackers)
    }

    // ---------------------------------------------------------- helpers

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            runCatching { transform(getJSONObject(index)) }.getOrNull()
        }
    }

    private fun JSONArray?.mapStrings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotBlank() } }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
