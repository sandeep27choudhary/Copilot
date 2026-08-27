package com.coffeeledger.app.data.sample

import com.coffeeledger.app.domain.model.Account
import com.coffeeledger.app.domain.model.AccountType
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.Direction
import com.coffeeledger.app.domain.model.PaymentMethod
import com.coffeeledger.app.domain.model.SourceType
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.TrackerKind
import com.coffeeledger.app.domain.model.TrackerPeriod
import com.coffeeledger.app.domain.model.Txn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.min

/**
 * A realistic three-month ledger used to seed a fresh install, so every screen has
 * something honest to show before the first SMS is ever read.
 *
 * The current month is tuned to the figures in the product brief: 78,632 spent, 16,695
 * received, 61,937 net outflow, against a 1,00,000 monthly budget. Everything is generated
 * relative to today, and same-month entries are clamped to today's date so the ledger never
 * shows a transaction that has not happened yet.
 */
object SampleData {

    const val ACCOUNT_HDFC = "acc-hdfc-4143"
    const val ACCOUNT_ICICI = "acc-icici-1234"
    const val ACCOUNT_SAVINGS = "acc-savings-9021"
    const val ACCOUNT_CARD = "acc-card-7788"

    fun accounts(): List<Account> = listOf(
        Account(ACCOUNT_HDFC, "HDFC Everyday", "HDFC Bank", "4143", AccountType.BANK, 1_18_400_00L),
        Account(ACCOUNT_ICICI, "ICICI Salary", "ICICI Bank", "1234", AccountType.BANK, 62_500_00L),
        Account(ACCOUNT_SAVINGS, "Savings", "HDFC Bank", "9021", AccountType.BANK, 1_00_000_00L),
        Account(ACCOUNT_CARD, "ICICI Card", "ICICI Bank", "7788", AccountType.CARD, 0L, includeInTotals = false),
    )

    fun trackers(): List<Tracker> = listOf(
        Tracker(
            id = "trk-monthly",
            title = "Monthly spending",
            kind = TrackerKind.SPENDING_LIMIT,
            period = TrackerPeriod.MONTHLY,
            targetMinor = 1_00_000_00L,
            sortOrder = 0,
        ),
        Tracker(
            id = "trk-food",
            title = "Food",
            kind = TrackerKind.SPENDING_LIMIT,
            period = TrackerPeriod.MONTHLY,
            targetMinor = 6_000_00L,
            categoryIds = listOf(Category.FOOD.id),
            sortOrder = 1,
        ),
        Tracker(
            id = "trk-groceries",
            title = "Groceries",
            kind = TrackerKind.SPENDING_LIMIT,
            period = TrackerPeriod.MONTHLY,
            targetMinor = 15_000_00L,
            categoryIds = listOf(Category.GROCERIES.id),
            sortOrder = 2,
        ),
        Tracker(
            id = "trk-savings",
            title = "Savings",
            kind = TrackerKind.SAVINGS_TARGET,
            period = TrackerPeriod.MONTHLY,
            targetMinor = 30_000_00L,
            accountIds = listOf(ACCOUNT_SAVINGS),
            sortOrder = 3,
        ),
        Tracker(
            id = "trk-emergency",
            title = "Emergency fund",
            kind = TrackerKind.GOAL,
            period = TrackerPeriod.ALL_TIME,
            targetMinor = 3_00_000_00L,
            manualProgressMinor = 1_20_000_00L,
            sortOrder = 4,
        ),
    )

    /** The full sample ledger, newest first. */
    fun transactions(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<Txn> {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val builder = Builder(today, zone)
        currentMonth(builder)
        priorMonth(builder, 1)
        priorMonth(builder, 2)
        return builder.result().sortedByDescending { it.occurredAt }
    }

    // ---------------------------------------------------------- current month

    /**
     * Spending here adds up to exactly 78,632 and credits to exactly 16,695, which is what
     * the dashboard in the brief shows. Transfers sit outside both figures.
     */
    private fun currentMonth(b: Builder) {
        // Housing and fixed costs
        b.debit(1, "09:12", 35_000_00L, "NoBroker Rentpay", Category.RENT, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(3, "10:04", 1_899_00L, "AIRTEL POSTPAID", Category.BILLS, ACCOUNT_HDFC, PaymentMethod.AUTO_DEBIT, "HDFC Bank")
        b.debit(4, "18:30", 948_00L, "ACT FIBERNET", Category.BILLS, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(5, "11:20", 1_950_00L, "BESCOM ELECTRICITY", Category.UTILITIES, ACCOUNT_HDFC, PaymentMethod.NET_BANKING, "HDFC Bank")

        // Subscriptions
        b.debit(2, "06:15", 649_00L, "NETFLIX.COM", Category.SUBSCRIPTIONS, ACCOUNT_CARD, PaymentMethod.AUTO_DEBIT, "ICICI Bank")
        b.debit(7, "06:15", 119_00L, "SPOTIFY INDIA", Category.SUBSCRIPTIONS, ACCOUNT_CARD, PaymentMethod.AUTO_DEBIT, "ICICI Bank")
        b.debit(12, "06:20", 1_499_00L, "AMAZON PRIME", Category.SUBSCRIPTIONS, ACCOUNT_CARD, PaymentMethod.AUTO_DEBIT, "ICICI Bank")

        // Groceries: 14,005
        b.debit(2, "11:38", 742_00L, "BLINKIT COMMERCE PVT LTD", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(5, "19:44", 1_284_00L, "BIGBASKET", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(8, "12:02", 486_00L, "BLINKIT GROCERY", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(11, "20:15", 3_120_00L, "DMART AVENUE SUPERMARTS", Category.GROCERIES, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")
        b.debit(14, "09:50", 918_00L, "ZEPTO NOW", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(17, "18:22", 1_640_00L, "BIGBASKET", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(19, "13:11", 655_00L, "BLINKIT COMMERCE PVT LTD", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(21, "17:05", 2_240_00L, "RELIANCE FRESH", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.CARD, "HDFC Bank")
        b.debit(23, "10:31", 1_180_00L, "LICIOUS", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(26, "11:38", 1_740_00L, "BLINKIT COMMERCE PVT LTD", Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")

        // Food: 4,180
        b.debit(3, "21:10", 486_00L, "ZOMATO LIMITED", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(6, "13:25", 322_00L, "SWIGGY BUNDL TECHNOLOGIES", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(9, "20:48", 654_00L, "ZOMATO LIMITED", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(12, "09:05", 285_00L, "THIRD WAVE COFFEE", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(15, "21:30", 512_00L, "SWIGGY BUNDL TECHNOLOGIES", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(18, "14:12", 240_00L, "BLUE TOKAI COFFEE", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(20, "20:02", 738_00L, "ZOMATO LIMITED", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(22, "13:40", 419_00L, "DOMINOS PIZZA", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(25, "20:55", 524_00L, "SWIGGY BUNDL TECHNOLOGIES", Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")

        // Shopping: 3,200
        b.debit(10, "16:20", 2_100_00L, "AMAZON SELLER SERVICES", Category.SHOPPING, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")
        b.debit(16, "19:08", 1_100_00L, "MYNTRA DESIGNS", Category.SHOPPING, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")

        // Transport: 1,800
        b.debit(4, "08:45", 268_00L, "UBER INDIA SYSTEMS", Category.TRANSPORT, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(9, "18:55", 342_00L, "OLA CABS", Category.TRANSPORT, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(13, "08:30", 190_00L, "RAPIDO", Category.TRANSPORT, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debit(18, "09:15", 640_00L, "INDIAN OIL PETROL", Category.TRANSPORT, ACCOUNT_HDFC, PaymentMethod.CARD, "HDFC Bank")
        b.debit(24, "19:40", 360_00L, "UBER INDIA SYSTEMS", Category.TRANSPORT, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")

        // Entertainment, health, travel, investment, other
        b.debit(16, "20:30", 900_00L, "BOOKMYSHOW", Category.ENTERTAINMENT, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")
        b.debit(11, "11:25", 1_200_00L, "APOLLO PHARMACY", Category.HEALTH, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(20, "10:10", 4_283_00L, "MAKEMYTRIP INDIA", Category.TRAVEL, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")
        b.debit(5, "07:00", 5_000_00L, "GROWW SIP MUTUAL FUND", Category.INVESTMENT, ACCOUNT_ICICI, PaymentMethod.AUTO_DEBIT, "ICICI Bank")
        b.debit(13, "15:45", 1_150_00L, "SHARMA KIRANA PVT LTD", Category.OTHER_EXPENSE, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debit(22, "17:20", 850_00L, "LOCAL SALON", Category.OTHER_EXPENSE, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")

        // Credits: 16,695
        b.credit(7, "10:02", 1_499_00L, "AMAZON REFUND", Category.REFUND, ACCOUNT_CARD, PaymentMethod.UPI, "ICICI Bank")
        b.credit(14, "19:26", 12_000_00L, "RAHUL VERMA", Category.PAYMENT_RECEIVED, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.credit(21, "12:00", 3_000_00L, "DESIGN FREELANCE INVOICE", Category.OTHER_INCOME, ACCOUNT_HDFC, PaymentMethod.IMPS, "HDFC Bank")
        b.credit(25, "23:59", 196_00L, "SAVINGS INTEREST", Category.OTHER_INCOME, ACCOUNT_HDFC, PaymentMethod.UNKNOWN, "HDFC Bank")

        // Transfers between the user's own accounts: never spending.
        b.transferOut(6, "10:00", 20_000_00L, "Savings", ACCOUNT_HDFC)
        b.transferIn(6, "10:01", 20_000_00L, "HDFC Everyday", ACCOUNT_SAVINGS)
        b.transferOut(15, "11:00", 25_000_00L, "ICICI Card", ACCOUNT_HDFC)
    }

    // ------------------------------------------------------------ history

    /** A settled month: salary in, rent out, and the same repeating commitments. */
    private fun priorMonth(b: Builder, monthsBack: Long) {
        val jitter = if (monthsBack == 1L) 1 else -1

        b.creditPrior(monthsBack, 28, "18:30", 1_25_000_00L, "ACME TECHNOLOGIES PAYROLL", Category.SALARY, ACCOUNT_ICICI, PaymentMethod.NEFT, "ICICI Bank")
        b.debitPrior(monthsBack, 1, "09:12", 35_000_00L, "NoBroker Rentpay", Category.RENT, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        b.debitPrior(monthsBack, 2, "06:15", 649_00L, "NETFLIX.COM", Category.SUBSCRIPTIONS, ACCOUNT_CARD, PaymentMethod.AUTO_DEBIT, "ICICI Bank")
        b.debitPrior(monthsBack, 7, "06:15", 119_00L, "SPOTIFY INDIA", Category.SUBSCRIPTIONS, ACCOUNT_CARD, PaymentMethod.AUTO_DEBIT, "ICICI Bank")
        b.debitPrior(monthsBack, 12, "06:20", 1_499_00L, "AMAZON PRIME", Category.SUBSCRIPTIONS, ACCOUNT_CARD, PaymentMethod.AUTO_DEBIT, "ICICI Bank")
        b.debitPrior(monthsBack, 3, "10:04", 1_899_00L, "AIRTEL POSTPAID", Category.BILLS, ACCOUNT_HDFC, PaymentMethod.AUTO_DEBIT, "HDFC Bank")
        b.debitPrior(monthsBack, 4, "18:30", 948_00L, "ACT FIBERNET", Category.BILLS, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debitPrior(monthsBack, 5, "11:20", (1_780 + jitter * 90) * 100L, "BESCOM ELECTRICITY", Category.UTILITIES, ACCOUNT_HDFC, PaymentMethod.NET_BANKING, "HDFC Bank")
        b.debitPrior(monthsBack, 5, "07:00", 5_000_00L, "GROWW SIP MUTUAL FUND", Category.INVESTMENT, ACCOUNT_ICICI, PaymentMethod.AUTO_DEBIT, "ICICI Bank")

        val groceryDays = listOf(2, 6, 10, 14, 18, 22, 26)
        groceryDays.forEachIndexed { index, day ->
            val amount = (1_150 + index * 190 + jitter * 60) * 100L
            val merchant = if (index % 2 == 0) "BLINKIT COMMERCE PVT LTD" else "BIGBASKET"
            b.debitPrior(monthsBack, day, "12:0$index", amount, merchant, Category.GROCERIES, ACCOUNT_HDFC, PaymentMethod.UPI, "PhonePe")
        }

        val foodDays = listOf(3, 8, 13, 17, 21, 25)
        foodDays.forEachIndexed { index, day ->
            val amount = (380 + index * 70 + jitter * 25) * 100L
            val merchant = if (index % 2 == 0) "ZOMATO LIMITED" else "SWIGGY BUNDL TECHNOLOGIES"
            b.debitPrior(monthsBack, day, "20:1$index", amount, merchant, Category.FOOD, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        }

        b.debitPrior(monthsBack, 9, "16:20", (2_400 + jitter * 400) * 100L, "AMAZON SELLER SERVICES", Category.SHOPPING, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")
        b.debitPrior(monthsBack, 19, "18:00", (1_600 + jitter * 200) * 100L, "FLIPKART INTERNET", Category.SHOPPING, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")
        b.debitPrior(monthsBack, 11, "08:45", (1_450 + jitter * 150) * 100L, "UBER INDIA SYSTEMS", Category.TRANSPORT, ACCOUNT_HDFC, PaymentMethod.UPI, "Google Pay")
        b.debitPrior(monthsBack, 23, "19:30", (1_100 + jitter * 300) * 100L, "PVR INOX", Category.ENTERTAINMENT, ACCOUNT_CARD, PaymentMethod.CARD, "ICICI Bank")

        b.transferOutPrior(monthsBack, 6, "10:00", 20_000_00L, "Savings", ACCOUNT_HDFC)
        b.transferInPrior(monthsBack, 6, "10:01", 20_000_00L, "HDFC Everyday", ACCOUNT_SAVINGS)
    }

    // ------------------------------------------------------------ builder

    private class Builder(private val today: LocalDate, private val zone: ZoneId) {
        private val items = mutableListOf<Txn>()
        private var sequence = 0

        fun result(): List<Txn> = items

        /** Same-month entries never land in the future. */
        private fun currentMonthMillis(day: Int, time: String): Long {
            val clamped = min(day, today.dayOfMonth)
            return millis(today.withDayOfMonth(1).plusDays((clamped - 1).toLong()), time)
        }

        private fun priorMonthMillis(monthsBack: Long, day: Int, time: String): Long {
            val base = today.withDayOfMonth(1).minusMonths(monthsBack)
            val clamped = min(day, base.lengthOfMonth())
            return millis(base.withDayOfMonth(clamped), time)
        }

        private fun millis(date: LocalDate, time: String): Long {
            val parts = time.split(":")
            val localTime = LocalTime.of(parts[0].toInt(), parts[1].toInt())
            return date.atTime(localTime).atZone(zone).toInstant().toEpochMilli()
        }

        fun debit(
            day: Int,
            time: String,
            amount: Long,
            merchantRaw: String,
            category: Category,
            accountId: String,
            method: PaymentMethod,
            sourceApp: String,
        ) = add(currentMonthMillis(day, time), amount, Direction.DEBIT, merchantRaw, category, accountId, method, sourceApp, false)

        fun credit(
            day: Int,
            time: String,
            amount: Long,
            merchantRaw: String,
            category: Category,
            accountId: String,
            method: PaymentMethod,
            sourceApp: String,
        ) = add(currentMonthMillis(day, time), amount, Direction.CREDIT, merchantRaw, category, accountId, method, sourceApp, false)

        fun debitPrior(
            monthsBack: Long,
            day: Int,
            time: String,
            amount: Long,
            merchantRaw: String,
            category: Category,
            accountId: String,
            method: PaymentMethod,
            sourceApp: String,
        ) = add(priorMonthMillis(monthsBack, day, time), amount, Direction.DEBIT, merchantRaw, category, accountId, method, sourceApp, false)

        fun creditPrior(
            monthsBack: Long,
            day: Int,
            time: String,
            amount: Long,
            merchantRaw: String,
            category: Category,
            accountId: String,
            method: PaymentMethod,
            sourceApp: String,
        ) = add(priorMonthMillis(monthsBack, day, time), amount, Direction.CREDIT, merchantRaw, category, accountId, method, sourceApp, false)

        fun transferOut(day: Int, time: String, amount: Long, toName: String, accountId: String) =
            add(currentMonthMillis(day, time), amount, Direction.DEBIT, toName, Category.TRANSFER_OUT, accountId, PaymentMethod.IMPS, "HDFC Bank", true)

        fun transferIn(day: Int, time: String, amount: Long, fromName: String, accountId: String) =
            add(currentMonthMillis(day, time), amount, Direction.CREDIT, fromName, Category.TRANSFER_IN, accountId, PaymentMethod.IMPS, "HDFC Bank", true)

        fun transferOutPrior(monthsBack: Long, day: Int, time: String, amount: Long, toName: String, accountId: String) =
            add(priorMonthMillis(monthsBack, day, time), amount, Direction.DEBIT, toName, Category.TRANSFER_OUT, accountId, PaymentMethod.IMPS, "HDFC Bank", true)

        fun transferInPrior(monthsBack: Long, day: Int, time: String, amount: Long, fromName: String, accountId: String) =
            add(priorMonthMillis(monthsBack, day, time), amount, Direction.CREDIT, fromName, Category.TRANSFER_IN, accountId, PaymentMethod.IMPS, "HDFC Bank", true)

        private fun add(
            occurredAt: Long,
            amount: Long,
            direction: Direction,
            merchantRaw: String,
            category: Category,
            accountId: String,
            method: PaymentMethod,
            sourceApp: String,
            isTransfer: Boolean,
        ) {
            sequence++
            val tail = accounts().firstOrNull { it.id == accountId }?.tail
            items.add(
                Txn(
                    id = "sample-%03d".format(sequence),
                    occurredAt = occurredAt,
                    amountMinor = amount,
                    direction = direction,
                    merchant = com.coffeeledger.app.domain.normalize.MerchantNormalizer.normalize(merchantRaw),
                    merchantRaw = merchantRaw,
                    category = category,
                    accountId = accountId,
                    accountTail = tail,
                    sourceType = SourceType.SMS,
                    sourceApp = sourceApp,
                    paymentMethod = method,
                    reference = "SAMPLE%09d".format(sequence * 7919),
                    isTransfer = isTransfer,
                ),
            )
        }
    }
}
