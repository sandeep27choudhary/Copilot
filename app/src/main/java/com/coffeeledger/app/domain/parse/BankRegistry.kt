package com.coffeeledger.app.domain.parse

/**
 * Maps Indian SMS sender IDs ("VM-HDFCBK", "AD-ICICIT", "JD-SBIINB") and in-body
 * signatures ("-HDFC Bank") to a readable institution name.
 *
 * Sender IDs are the DLT header format: a two-letter operator prefix, a hyphen, then a
 * six-character entity code, sometimes with a one-character trailing route digit.
 */
object BankRegistry {

    private data class Entry(val name: String, val codes: List<String>, val bodyHints: List<String>)

    private val ENTRIES = listOf(
        Entry("HDFC Bank", listOf("hdfcbk", "hdfcbn", "hdfcba"), listOf("hdfc bank", "hdfc")),
        Entry("ICICI Bank", listOf("icicib", "icicit", "icicin"), listOf("icici bank", "icici")),
        Entry("State Bank of India", listOf("sbiinb", "sbibnk", "atmsbi", "sbiupi", "sbicrd"), listOf("state bank of india", "sbi")),
        Entry("Axis Bank", listOf("axisbk", "axisbn"), listOf("axis bank", "axis")),
        Entry("Kotak Bank", listOf("kotakb", "kotak"), listOf("kotak mahindra", "kotak")),
        Entry("Yes Bank", listOf("yesbnk", "yesbk"), listOf("yes bank")),
        Entry("IDFC First Bank", listOf("idfcfb", "idfcbk"), listOf("idfc first", "idfc")),
        Entry("IndusInd Bank", listOf("indusb", "indbnk"), listOf("indusind")),
        Entry("Punjab National Bank", listOf("pnbsms", "pnbbnk"), listOf("punjab national", "pnb")),
        Entry("Bank of Baroda", listOf("bobtxn", "bobsms", "bobibn"), listOf("bank of baroda", "bob")),
        Entry("Canara Bank", listOf("canbnk", "canara"), listOf("canara bank")),
        Entry("Union Bank", listOf("unionb", "ubioff"), listOf("union bank")),
        Entry("RBL Bank", listOf("rblbnk", "rblcrd"), listOf("rbl bank")),
        Entry("AU Small Finance Bank", listOf("aubank"), listOf("au small finance", "au bank")),
        Entry("Federal Bank", listOf("fedbnk"), listOf("federal bank")),
        Entry("DBS Bank", listOf("dbsbnk", "dbsmob"), listOf("dbs bank", "digibank")),
        Entry("Citibank", listOf("citibk", "citiin"), listOf("citibank")),
        Entry("HSBC", listOf("hsbcin", "hsbcbk"), listOf("hsbc")),
        Entry("American Express", listOf("amexin", "amexbk"), listOf("american express", "amex")),
        Entry("Standard Chartered", listOf("scbank", "scbbnk"), listOf("standard chartered")),
        Entry("Bandhan Bank", listOf("bandhn"), listOf("bandhan bank")),
        Entry("Paytm Payments Bank", listOf("paytmb", "ptmbnk"), listOf("paytm payments bank")),
        Entry("PhonePe", listOf("phonpe", "phnpay", "ppepay"), listOf("phonepe")),
        Entry("Google Pay", listOf("gpayin", "googpy"), listOf("google pay", "gpay")),
        Entry("Amazon Pay", listOf("amzpay", "amznpy"), listOf("amazon pay")),
        Entry("Slice", listOf("sliceit", "slcpay"), listOf("slice")),
        Entry("Cred", listOf("credcb", "credit"), listOf("cred club")),
        Entry("Jupiter", listOf("jupitr"), listOf("jupiter money")),
    )

    /** @return a readable institution name, or null when the sender is not recognised. */
    fun identify(sender: String?, body: String? = null): String? {
        val code = senderCode(sender)
        if (code != null) {
            ENTRIES.firstOrNull { entry -> entry.codes.any { code.startsWith(it) } }
                ?.let { return it.name }
        }
        if (!body.isNullOrBlank()) {
            val text = body.lowercase()
            // Longest hint first so "hdfc bank" beats a bare "hdfc" in another entry.
            var best: String? = null
            var bestLength = 0
            for (entry in ENTRIES) {
                for (hint in entry.bodyHints) {
                    if (hint.length > bestLength && text.contains(hint)) {
                        best = entry.name
                        bestLength = hint.length
                    }
                }
            }
            if (best != null) return best
        }
        return null
    }

    /** Strips the DLT operator prefix and any trailing route digit: "VM-HDFCBK" -> "hdfcbk". */
    internal fun senderCode(sender: String?): String? {
        if (sender.isNullOrBlank()) return null
        val trimmed = sender.trim().lowercase()
        if (trimmed.all { it.isDigit() || it == '+' }) return null
        val afterPrefix = trimmed.substringAfterLast('-')
        return afterPrefix.filter { it.isLetterOrDigit() }.ifBlank { null }
    }

    fun knownInstitutions(): List<String> = ENTRIES.map { it.name }
}
