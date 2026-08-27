package com.coffeeledger.app.domain.parse

import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.money.Money

/**
 * A fully local bank/UPI SMS parser.
 *
 * Everything here is pure text processing on the device: no message, or any part of one,
 * is ever sent anywhere. The parser is deliberately conservative — it would rather reject
 * a message than invent a transaction, because a wrong ledger entry is worse than a
 * missing one the user can add by hand.
 */
object SmsTransactionParser {

    /** Below this confidence the transaction is stored but flagged for user review. */
    const val REVIEW_THRESHOLD = 0.55f

    // ---------------------------------------------------------------- guards

    private val OTP_PATTERNS = listOf(
        Regex("""\botp\b""", RegexOption.IGNORE_CASE),
        Regex("""one[\s-]?time[\s-]?password""", RegexOption.IGNORE_CASE),
        Regex("""verification code""", RegexOption.IGNORE_CASE),
        Regex("""do not share""", RegexOption.IGNORE_CASE),
        Regex("""\bcvv\b""", RegexOption.IGNORE_CASE),
    )

    private val PROMO_PATTERNS = listOf(
        Regex("""pre[\s-]?approved""", RegexOption.IGNORE_CASE),
        Regex("""apply now""", RegexOption.IGNORE_CASE),
        Regex("""loan offer|personal loan of|instant loan""", RegexOption.IGNORE_CASE),
        Regex("""you are eligible""", RegexOption.IGNORE_CASE),
        Regex("""click here""", RegexOption.IGNORE_CASE),
        Regex("""t&c apply|terms and conditions apply""", RegexOption.IGNORE_CASE),
        Regex("""flat \d+% off|use code""", RegexOption.IGNORE_CASE),
        Regex("""upgrade your card|increase your limit""", RegexOption.IGNORE_CASE),
    )

    private val FUTURE_PATTERNS = listOf(
        Regex("""will be (?:debited|deducted|charged|auto[\s-]?debited)""", RegexOption.IGNORE_CASE),
        Regex("""is due|due on|due by|payment due""", RegexOption.IGNORE_CASE),
        Regex("""mandate (?:has been )?(?:registered|created|approved)""", RegexOption.IGNORE_CASE),
        Regex("""has requested|collect request|requesting money|payment request""", RegexOption.IGNORE_CASE),
        Regex("""to be debited""", RegexOption.IGNORE_CASE),
    )

    private val FAILURE_PATTERNS = listOf(
        Regex("""(?:has |was |)(?:failed|declined|unsuccessful|rejected)""", RegexOption.IGNORE_CASE),
        Regex("""could not be (?:processed|completed)""", RegexOption.IGNORE_CASE),
        Regex("""insufficient (?:balance|funds)""", RegexOption.IGNORE_CASE),
    )

    // --------------------------------------------------------------- amounts

    private val AMOUNT_PREFIXED = Regex(
        """(?:inr|rs|₹)\s*\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val AMOUNT_SUFFIXED = Regex(
        """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:inr|rs\b|rupees)""",
        RegexOption.IGNORE_CASE,
    )

    /** Words that mark the following number as a balance or a limit, never a transaction. */
    private val BALANCE_CONTEXT = Regex(
        """(?:bal|balance|lmt|limit|outstanding|o/s|available|avbl|avl|min\s?due|total\s?due)[^0-9a-z]{0,12}$""",
        RegexOption.IGNORE_CASE,
    )

    // ------------------------------------------------------------- direction

    private val DEBIT_SIGNALS = listOf(
        Regex("""\bdebited\b""", RegexOption.IGNORE_CASE),
        Regex("""\bspent\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwithdrawn\b|\bwithdrawal\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpaid\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsent\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdeducted\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpurchased?\b""", RegexOption.IGNORE_CASE),
        Regex("""\btransferred to\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdebit\b(?!\s*card)""", RegexOption.IGNORE_CASE),
    )

    private val CREDIT_SIGNALS = listOf(
        Regex("""\bcredited\b""", RegexOption.IGNORE_CASE),
        Regex("""\breceived\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdeposited\b""", RegexOption.IGNORE_CASE),
        Regex("""\brefunded\b|\brefund\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcredit\b(?!\s*card)""", RegexOption.IGNORE_CASE),
    )

    // ------------------------------------------------------------ identifiers

    private val ACCOUNT_TAIL = Regex(
        """(?:a/?c|acct|account|card|ending|ac no|a/c no)[^0-9a-z]{0,6}(?:[xX*•]{1,12}\s*)?(\d{3,6})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ACCOUNT_TAIL_MASKED = Regex("""[xX*•]{2,}\s*(\d{3,6})\b""")

    private val REFERENCE = Regex(
        """(?:upi\s*(?:ref|rrn)|ref(?:erence)?\s*(?:no|number|id)?|rrn|txn\s*(?:id|no)|transaction\s*id|utr)[^0-9a-z]{0,4}([A-Za-z0-9]{6,25})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val VPA = Regex("""\b([A-Za-z0-9][A-Za-z0-9._-]{1,40})@([A-Za-z]{2,20})\b""")

    /** Trailing boundary shared by every merchant pattern. */
    private const val END = """(?=\s*(?:$|[\n.,;!]|\bon\b|\bref\b|\brrn\b|\butr\b|\btxn\b|\bupi\b|\bvia\b|\bavl\b|\bavbl\b|\bbal\b|\blmt\b|\blimit\b|\bdated\b|\binfo\b|\bnot you\b|\bif not\b|\byour\b|\ba/?c\b|-\s))"""

    private val MERCHANT_TO = Regex(
        """\b(?:to|at|towards|in favou?r of)\s+(?:vpa\s+)?([A-Za-z][A-Za-z0-9 &'./*_-]{1,45}?)$END""",
        RegexOption.IGNORE_CASE,
    )
    private val MERCHANT_FROM = Regex(
        """\bfrom\s+([A-Za-z][A-Za-z0-9 &'./*_-]{1,45}?)$END""",
        RegexOption.IGNORE_CASE,
    )

    private val MERCHANT_PATTERNS = listOf(
        MERCHANT_TO,
        MERCHANT_FROM,
        Regex("""\bupi/(?:p2m|p2a|cr|dr)?/?\d*/?([A-Za-z][A-Za-z0-9 ._-]{2,35}?)(?:/|$END)""", RegexOption.IGNORE_CASE),
        Regex("""\binfo[:\s-]+(?:upi/)?([A-Za-z][A-Za-z0-9 &'./*_-]{2,40}?)$END""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:trf|transfer)\s+(?:to|from)\s+([A-Za-z][A-Za-z0-9 &'./*_-]{2,40}?)$END""", RegexOption.IGNORE_CASE),
    )

    /** Captures that are structural words rather than an actual counterparty. */
    private val MERCHANT_STOPWORDS = setOf(
        "your account", "your a/c", "your card", "account", "a/c", "acct", "card",
        "upi", "bank", "you", "your", "self", "the", "us", "beneficiary", "payee",
        "your bank account", "your savings account", "your wallet", "wallet",
    )

    private val BALANCE_AMOUNT = Regex(
        """(?:avl|avbl|available|closing|clsg|updated)?\s*(?:bal|balance)[^0-9]{0,12}(?:inr|rs|₹)?\s*\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    // ----------------------------------------------------------------- entry

    /** Cheap pre-filter used before doing any real work on a message. */
    fun looksFinancial(body: String): Boolean {
        val text = body.lowercase()
        val hasCurrency = text.contains("rs") || text.contains("inr") || text.contains("₹")
        if (!hasCurrency) return false
        return FINANCIAL_HINTS.any { text.contains(it) }
    }

    private val FINANCIAL_HINTS = listOf(
        "debit", "credit", "spent", "paid", "received", "upi", "transaction",
        "txn", "withdraw", "transfer", "payment", "a/c", "account", "card",
    )

    fun parse(message: SmsMessage): ParseResult {
        val body = message.body
        if (!looksFinancial(body)) return ParseResult.Rejected(RejectionReason.NOT_FINANCIAL)
        if (OTP_PATTERNS.any { it.containsMatchIn(body) }) {
            return ParseResult.Rejected(RejectionReason.OTP_OR_VERIFICATION)
        }
        if (FAILURE_PATTERNS.any { it.containsMatchIn(body) }) {
            return ParseResult.Rejected(RejectionReason.FAILED_TRANSACTION)
        }
        if (FUTURE_PATTERNS.any { it.containsMatchIn(body) }) {
            return ParseResult.Rejected(RejectionReason.FUTURE_OR_MANDATE)
        }
        if (PROMO_PATTERNS.any { it.containsMatchIn(body) }) {
            return ParseResult.Rejected(RejectionReason.PROMOTIONAL)
        }

        val amount = extractAmount(body) ?: return ParseResult.Rejected(RejectionReason.NO_AMOUNT)
        val direction = extractDirection(body) ?: return ParseResult.Rejected(RejectionReason.NO_DIRECTION)

        val institution = BankRegistry.identify(message.sender, body)
        val merchantRaw = extractMerchant(body, direction, institution)
        val tail = extractAccountTail(body)
        val reference = REFERENCE.find(body)?.groupValues?.get(1)
        val method = extractPaymentMethod(body)
        val occurredAt = SmsDateParser.parse(body, message.receivedAt)
        val balance = extractBalance(body)

        val confidence = scoreConfidence(
            merchant = merchantRaw,
            tail = tail,
            reference = reference,
            institution = institution,
            method = method,
        )

        return ParseResult.Success(
            ParsedTransaction(
                amountMinor = amount,
                direction = direction,
                merchantRaw = merchantRaw,
                accountTail = tail,
                institution = institution,
                reference = reference,
                occurredAt = occurredAt,
                paymentMethod = method,
                balanceMinor = balance,
                rawBody = body,
                sender = message.sender,
                confidence = confidence,
            ),
        )
    }

    // ------------------------------------------------------------- internals

    /**
     * Picks the transaction amount, skipping any figure that a balance or limit word
     * introduces. Without this a message ending in "Avl Bal Rs.35,000" would book the
     * balance as the spend.
     */
    internal fun extractAmount(body: String): Long? {
        val candidates = buildList {
            AMOUNT_PREFIXED.findAll(body).forEach { add(it.range.first to it.groupValues[1]) }
            AMOUNT_SUFFIXED.findAll(body).forEach { add(it.range.first to it.groupValues[1]) }
        }.sortedBy { it.first }

        for ((start, text) in candidates) {
            val prefix = body.substring(maxOf(0, start - 28), start)
            if (BALANCE_CONTEXT.containsMatchIn(prefix)) continue
            val minor = Money.parseAmount(text) ?: continue
            if (minor <= 0L) continue
            return minor
        }
        return null
    }

    internal fun extractBalance(body: String): Long? {
        val match = BALANCE_AMOUNT.find(body) ?: return null
        return Money.parseAmount(match.groupValues[1])
    }

    /**
     * The earliest strong verb wins. In a self-transfer message ("debited from A and
     * credited to B") that is the debit, which is the leg that left the tracked account.
     */
    internal fun extractDirection(body: String): Direction? {
        val debitAt = DEBIT_SIGNALS.mapNotNull { it.find(body)?.range?.first }.minOrNull()
        val creditAt = CREDIT_SIGNALS.mapNotNull { it.find(body)?.range?.first }.minOrNull()
        return when {
            debitAt == null && creditAt == null -> null
            creditAt == null -> Direction.DEBIT
            debitAt == null -> Direction.CREDIT
            debitAt <= creditAt -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }

    internal fun extractAccountTail(body: String): String? {
        ACCOUNT_TAIL.find(body)?.let { return it.groupValues[1] }
        ACCOUNT_TAIL_MASKED.find(body)?.let { return it.groupValues[1] }
        return null
    }

    internal fun extractMerchant(
        body: String,
        direction: Direction,
        institution: String?,
    ): String? {
        // A UPI handle is the most reliable counterparty signal when one is present.
        VPA.find(body)?.let { match ->
            val handle = match.groupValues[1]
            if (!handle.all { it.isDigit() } && handle.length >= 3) {
                return handle.replace('.', ' ').replace('_', ' ').trim()
            }
        }

        // For money coming in, "from X" names the payer; for money going out it is "to X".
        val ordered = if (direction == Direction.CREDIT) {
            listOf(MERCHANT_FROM) + MERCHANT_PATTERNS.filter { it !== MERCHANT_FROM }
        } else {
            MERCHANT_PATTERNS
        }

        for (pattern in ordered) {
            val candidate = pattern.find(body)?.groupValues?.getOrNull(1)?.trim() ?: continue
            val cleaned = candidate.trim(' ', '.', ',', '-', '*', '/')
            if (!isPlausibleMerchant(cleaned, institution)) continue
            return cleaned
        }
        return null
    }

    private fun isPlausibleMerchant(candidate: String, institution: String?): Boolean {
        if (candidate.length < 2) return false
        if (candidate.none { it.isLetter() }) return false
        val lower = candidate.lowercase()
        if (lower in MERCHANT_STOPWORDS) return false
        if (MERCHANT_STOPWORDS.any { lower.startsWith("$it ") && lower.length < it.length + 4 }) return false
        // "debited from HDFC Bank a/c" describes the account, not a counterparty.
        if (institution != null && lower.startsWith(institution.lowercase())) return false
        if (lower.matches(Regex("""(?:a/?c|acct|card|upi|bank)\b.*"""))) return false
        return true
    }

    internal fun extractPaymentMethod(body: String): PaymentMethod {
        val text = body.lowercase()
        return when {
            text.contains("atm") || text.contains("withdrawn") -> PaymentMethod.ATM
            text.contains("nach") || text.contains("mandate") || text.contains("autopay") ||
                text.contains("auto debit") || text.contains("e-mandate") -> PaymentMethod.AUTO_DEBIT
            text.contains("upi") || VPA.containsMatchIn(body) -> PaymentMethod.UPI
            text.contains("imps") -> PaymentMethod.IMPS
            text.contains("neft") || text.contains("rtgs") -> PaymentMethod.NEFT
            text.contains("card") -> PaymentMethod.CARD
            text.contains("net banking") || text.contains("netbanking") ||
                text.contains("internet banking") -> PaymentMethod.NET_BANKING
            else -> PaymentMethod.UNKNOWN
        }
    }

    /**
     * Confidence is the share of corroborating signals found. It drives the "needs review"
     * flag rather than whether the transaction is stored, so nothing is silently dropped.
     */
    internal fun scoreConfidence(
        merchant: String?,
        tail: String?,
        reference: String?,
        institution: String?,
        method: PaymentMethod,
    ): Float {
        var score = 0.4f
        if (!merchant.isNullOrBlank()) score += 0.25f
        if (!tail.isNullOrBlank()) score += 0.15f
        if (!reference.isNullOrBlank()) score += 0.1f
        if (!institution.isNullOrBlank()) score += 0.05f
        if (method != PaymentMethod.UNKNOWN) score += 0.05f
        return score.coerceAtMost(1f)
    }
}
