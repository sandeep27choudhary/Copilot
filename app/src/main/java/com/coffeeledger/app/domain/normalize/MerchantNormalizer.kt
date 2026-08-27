package com.coffeeledger.app.domain.normalize

import com.coffeeledger.app.domain.model.Category

/**
 * A known merchant: the tokens that identify it in raw bank text, the name we show,
 * and the category it should land in unless the user says otherwise.
 */
data class MerchantProfile(
    val canonical: String,
    val tokens: List<String>,
    val category: Category?,
)

/**
 * Turns the noise a bank puts in an SMS ("BLINKIT COMMERCE PVT LTD", "PAYTM*ZOMATO",
 * "swiggy.food@icici") into one stable display name ("Blinkit", "Zomato", "Swiggy").
 *
 * Matching is deliberately token based rather than substring based, so "AMAZON" does not
 * match "AMAZONITE JEWELLERS" and short tokens like "OLA" do not match "SOLAR".
 */
object MerchantNormalizer {

    private val CATALOG: List<MerchantProfile> = listOf(
        // Groceries and quick commerce
        p("Blinkit", Category.GROCERIES, "blinkit", "grofers"),
        p("Zepto", Category.GROCERIES, "zepto", "geddit"),
        p("BigBasket", Category.GROCERIES, "bigbasket", "bbdaily", "innovative retail"),
        p("Swiggy Instamart", Category.GROCERIES, "instamart"),
        p("DMart", Category.GROCERIES, "dmart", "avenue supermarts"),
        p("Reliance Fresh", Category.GROCERIES, "reliance fresh", "reliance smart"),

        // Housing
        p("NoBroker", Category.RENT, "nobroker", "no broker"),
        p("Housing.com", Category.RENT, "housing com"),
        p("More Retail", Category.GROCERIES, "more retail", "more megastore"),
        p("Licious", Category.GROCERIES, "licious"),
        p("Country Delight", Category.GROCERIES, "country delight"),

        // Food and delivery
        p("Zomato", Category.FOOD, "zomato", "eternal ltd"),
        p("Swiggy", Category.FOOD, "swiggy", "bundl technologies"),
        p("Domino's", Category.FOOD, "dominos", "domino s", "jubilant foodworks"),
        p("Starbucks", Category.FOOD, "starbucks", "tata starbucks"),
        p("McDonald's", Category.FOOD, "mcdonalds", "mcdonald s", "hardcastle"),
        p("KFC", Category.FOOD, "kfc"),
        p("Third Wave Coffee", Category.FOOD, "third wave"),
        p("Blue Tokai", Category.FOOD, "blue tokai"),
        p("EatSure", Category.FOOD, "eatsure", "faasos", "behrouz", "ovenstory"),

        // Shopping
        p("Amazon", Category.SHOPPING, "amazon", "amzn", "clicktech retail"),
        p("Flipkart", Category.SHOPPING, "flipkart", "fkrt"),
        p("Myntra", Category.SHOPPING, "myntra"),
        p("Ajio", Category.SHOPPING, "ajio"),
        p("Nykaa", Category.SHOPPING, "nykaa"),
        p("Meesho", Category.SHOPPING, "meesho", "fashnear"),
        p("IKEA", Category.SHOPPING, "ikea"),
        p("Decathlon", Category.SHOPPING, "decathlon"),
        p("Croma", Category.SHOPPING, "croma", "infiniti retail"),
        p("Apple", Category.SHOPPING, "apple india", "apple com bill"),

        // Transport and travel
        p("Uber", Category.TRANSPORT, "uber"),
        p("Ola", Category.TRANSPORT, "olacabs", "ola cabs", "ani technologies"),
        p("Rapido", Category.TRANSPORT, "rapido", "roppen"),
        p("Namma Yatri", Category.TRANSPORT, "namma yatri", "nammayatri"),
        p("IRCTC", Category.TRAVEL, "irctc"),
        p("MakeMyTrip", Category.TRAVEL, "makemytrip", "mmt", "make my trip"),
        p("IndiGo", Category.TRAVEL, "indigo", "interglobe aviation"),
        p("Air India", Category.TRAVEL, "air india"),
        p("Cleartrip", Category.TRAVEL, "cleartrip"),
        p("Indian Oil", Category.TRANSPORT, "indian oil", "indianoil", "iocl"),
        p("HP Petrol", Category.TRANSPORT, "hindustan petroleum", "hpcl"),
        p("FASTag", Category.TRANSPORT, "fastag", "netc"),

        // Subscriptions and entertainment
        p("Netflix", Category.SUBSCRIPTIONS, "netflix"),
        p("Spotify", Category.SUBSCRIPTIONS, "spotify"),
        p("Amazon Prime", Category.SUBSCRIPTIONS, "prime video", "amazon prime"),
        p("YouTube Premium", Category.SUBSCRIPTIONS, "youtube premium", "google youtube"),
        p("Disney+ Hotstar", Category.SUBSCRIPTIONS, "hotstar", "disney"),
        p("Google One", Category.SUBSCRIPTIONS, "google one", "google storage"),
        p("iCloud", Category.SUBSCRIPTIONS, "icloud"),
        p("BookMyShow", Category.ENTERTAINMENT, "bookmyshow", "bigtree"),
        p("PVR INOX", Category.ENTERTAINMENT, "pvr", "inox leisure"),

        // Bills and utilities
        p("Airtel", Category.BILLS, "airtel", "bharti airtel"),
        p("Jio", Category.BILLS, "reliance jio", "jio prepaid", "jio postpaid", "jiofiber"),
        p("Vi", Category.BILLS, "vodafone idea", "vi postpaid"),
        p("ACT Fibernet", Category.BILLS, "act fibernet", "atria convergence"),
        p("BESCOM", Category.UTILITIES, "bescom"),
        p("Tata Power", Category.UTILITIES, "tata power"),
        p("Adani Electricity", Category.UTILITIES, "adani electricity"),
        p("Indane Gas", Category.UTILITIES, "indane", "hp gas", "bharat gas"),

        // Health, education, investment
        p("Apollo Pharmacy", Category.HEALTH, "apollo pharmacy", "apollo hospital"),
        p("PharmEasy", Category.HEALTH, "pharmeasy", "axelia"),
        p("Practo", Category.HEALTH, "practo"),
        p("Cult.fit", Category.HEALTH, "cult fit", "cultfit", "curefit"),
        p("Unacademy", Category.EDUCATION, "unacademy"),
        p("Coursera", Category.EDUCATION, "coursera"),
        p("Zerodha", Category.INVESTMENT, "zerodha"),
        p("Groww", Category.INVESTMENT, "groww", "nextbillion"),
        p("Kuvera", Category.INVESTMENT, "kuvera"),

        // Payment apps: these are rails, not merchants, but they do show up alone
        p("PhonePe", null, "phonepe", "phone pe"),
        p("Google Pay", null, "google pay", "googlepay", "gpay", "okgoogle"),
        p("Paytm", null, "paytm", "one97"),
        p("Amazon Pay", null, "amazon pay", "amazonpay"),
        p("Cred", null, "cred club", "cred pay", "dreamplug"),
        p("BHIM", null, "bhim upi"),
    )

    /** Legal-form and marketplace noise that never belongs in a display name. */
    private val NOISE_WORDS = setOf(
        "pvt", "private", "ltd", "limited", "llp", "inc", "corp", "co", "company",
        "india", "indian", "technologies", "technology", "tech", "solutions", "services",
        "enterprises", "retail", "commerce", "internet", "digital", "online", "store",
        "stores", "traders", "trading", "and", "the", "payment", "payments",
    )

    private val WORD_SPLIT = Regex("""[^A-Za-z0-9]+""")

    /**
     * @return the canonical merchant name, or a cleaned-up version of [raw] when the
     * merchant is not in the catalog. Never returns an empty string for non-blank input.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown"
        val haystack = tokenize(raw)
        matchProfile(haystack)?.let { return it.canonical }
        return prettify(raw)
    }

    /** The category the catalog suggests for [raw], or null when the merchant is unknown. */
    fun suggestedCategory(raw: String?): Category? {
        if (raw.isNullOrBlank()) return null
        return matchProfile(tokenize(raw))?.category
    }

    /** True when [raw] names a payment rail (PhonePe, GPay) rather than a real merchant. */
    fun isPaymentApp(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val profile = matchProfile(tokenize(raw)) ?: return false
        return profile.category == null
    }

    fun knownMerchantNames(): List<String> = CATALOG.map { it.canonical }.distinct().sorted()

    private fun matchProfile(haystack: String): MerchantProfile? {
        var best: MerchantProfile? = null
        var bestLength = 0
        for (profile in CATALOG) {
            for (token in profile.tokens) {
                // Padded containment means we match whole tokens only.
                if (haystack.contains(" $token ") && token.length > bestLength) {
                    best = profile
                    bestLength = token.length
                }
            }
        }
        return best
    }

    /** Lower-cases, strips punctuation and pads with spaces so token matching is exact. */
    private fun tokenize(raw: String): String {
        val words = raw.lowercase().split(WORD_SPLIT).filter { it.isNotEmpty() }
        return " " + words.joinToString(" ") + " "
    }

    /**
     * Best-effort cleanup for merchants outside the catalog: drop legal-form noise,
     * drop reference-number fragments, and title-case what is left.
     */
    private fun prettify(raw: String): String {
        val words = raw.split(WORD_SPLIT).filter { it.isNotEmpty() }
        val kept = words.filter { word ->
            val lower = word.lowercase()
            lower !in NOISE_WORDS &&
                !lower.matches(Regex("""\d{4,}""")) &&
                !lower.matches(Regex("""[a-z]*\d{6,}[a-z]*"""))
        }
        val source = kept.ifEmpty { words }
        if (source.isEmpty()) return "Unknown"
        return source.take(4).joinToString(" ") { titleCase(it) }
    }

    private fun titleCase(word: String): String = when {
        word.length <= 3 && word.all { it.isUpperCase() } -> word
        word.all { it.isDigit() } -> word
        else -> word.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

    private fun p(canonical: String, category: Category?, vararg tokens: String) =
        MerchantProfile(canonical, tokens.toList(), category)
}
