# Ledger

A minimalist, privacy-first personal finance tracker and advisor for Android.

Transactions are collected from bank SMS, UPI apps, CSV exports and PDF statements,
then parsed, categorised, analysed and answered questions about **entirely on the
device**. There is no account, no server, and no network permission.

---

## The privacy model

The guarantee is enforced by the platform, not by a policy page:

| Claim | How it is enforced |
| --- | --- |
| No financial data leaves the device | `AndroidManifest.xml` declares **no `INTERNET` permission**, so the process cannot open a socket |
| Transactions stored locally | Room over **SQLCipher**; every database page is encrypted at rest |
| Encryption keys protected | 32 random bytes sealed with an **AES-GCM key inside the Android Keystore** that cannot be exported |
| SMS processed locally | `SmsTransactionParser` is pure text processing; message bodies go to the encrypted database and nowhere else |
| Analytics processed locally | `AnalyticsEngine`, `InsightGenerator`, `FinancialHealth` are pure functions over a `List<Txn>` |
| Advisor processed locally | `LocalAdvisor` is rule-based intent matching over the in-memory ledger — no model call |
| Not in cloud backup | `allowBackup="false"` plus `data_extraction_rules.xml` excluding every domain |
| Erase means erase | "Delete all data" wipes the tables **and destroys the Keystore key**, so the file left behind cannot be decrypted |

The Privacy screen lists each of these alongside the live state of every permission.

---

## Architecture

```
SMS receiver / inbox scan ─┐
UPI + bank messages        │
CSV import ────────────────┼──▶ parser ──▶ merchant normaliser ──▶ categoriser
PDF statement import       │                                            │
manual entry ──────────────┘                                            ▼
                                                    encrypted local database (Room + SQLCipher)
                                                                        │
                                        ┌───────────────────────────────┼───────────────────┐
                                        ▼                               ▼                   ▼
                                 analytics engine              tracker progress       local advisor
                                        │                               │                   │
                                        └───────────────────────────────┴───────────────────┘
                                                                        ▼
                                                              Compose UI (5 tabs)
```

Cloud connectivity is not merely optional — it is absent. The layering is such that an
optional end-to-end encrypted backup could be added later as a new sink behind the
repository, without any layer below it learning about the network.

### Source layout

```
app/src/main/java/com/coffeeledger/app/
  domain/            pure Kotlin, no Android imports, fully unit tested
    money/           paise arithmetic and Indian digit grouping (₹1,20,000)
    model/           Txn, Account, Category, Tracker, TrackerProgress
    parse/           SMS parser, bank sender registry, date parser
    normalize/       merchant catalog and normalisation
    categorize/      rule precedence and self-transfer detection
    analytics/       summaries, trends, recurring detection, insights, health score
    advisor/         intent matching and answer construction
    importer/        CSV parser, PDF text extractor, PDF statement parser
    query/           timeline filtering
  data/
    security/        Keystore-backed database key manager
    db/              Room entities, DAOs, SQLCipher open, entity <-> domain mappers
    repo/            LedgerRepository, SettingsRepository
    io/              JSON backup and CSV export/import
    sample/          the seeded sample ledger
  sms/               broadcast receiver and inbox scanner
  ui/                Compose theme, components and the five screens
```

---

## Features

**Transaction engine.** One ledger for every bank and app. Each entry carries date, time,
amount, direction, merchant (raw and normalised), category, account, payment method,
reference, notes, source app and source type. De-duplication is keyed on the transaction
reference where one exists, so re-scanning the inbox is idempotent.

**SMS detection.** Handles the common Indian formats — HDFC, ICICI, SBI, Axis, Kotak and
others — across UPI, card, ATM, IMPS/NEFT and auto-debit. It deliberately rejects OTPs,
marketing, mandate notices, collect requests and failed payments, and it will not mistake
`Avl Bal Rs.35,120.50` for the transaction amount. Anything it is unsure about is stored
but flagged for review rather than dropped.

**Merchant normalisation.** `BLINKIT COMMERCE PVT LTD`, `Blinkit Grocery` and `BLINKIT`
all become **Blinkit**. Matching is token-based, so `AMAZONITE JEWELLERS` is not Amazon.

**Credit, debit and transfers.** Transfers between the user's own accounts are detected and
never counted as spending, anywhere — not in the dashboard, not in a tracker, not in an
insight, not in an advisor answer.

**Trackers.** One primitive covers a monthly cap, a category limit, a savings target and a
long-running goal. Progress is derived from the ledger; goals can also hold a manually
recorded starting amount.

**Insights and health.** Pace against the calendar, category shifts, frequent merchants,
recurring commitments, outliers and cash flow. The 0–100 health score always shows the five
weighted components and the sentence explaining each one.

**Advisor.** "Where did I spend the most?", "Can I afford a ₹60,000 purchase?", "What
changed compared with last month?" — matched to an intent, answered from structured local
data. Forward-looking answers are explicitly labelled as general information rather than
investment advice.

**Import and export.** CSV covers most bank exports (separate debit/credit columns, or a
single amount column with a Dr/Cr marker or a sign). PDF import uses a **dependency-free
extractor written for this app** — content streams are inflated and text operators replayed
— rather than pulling in a third-party PDF library to handle financial documents. Export
produces a readable JSON backup or a CSV you choose the location for.

---

## Design

Coffee and paper. A warm cream page, slightly lighter cards, hairline warm-beige borders,
dark brown ink and a single medium-coffee accent. Two muted signal colours (moss for money
in, brick for over budget) and nothing else. Amounts are set in tabular figures so a column
of numbers lines up, and the largest thing on any screen is always a number.

The palette lives in `ui/theme/Color.kt`, the type scale in `ui/theme/Type.kt`. Meters are
plain horizontal bars — a track and a fill — because the tracker is the centre of the
product, not a chart gallery. The bottom bar is text-only for the same reason.

---

## Building

```bash
./gradlew assembleDebug
```

Requires the Android SDK (compileSdk 35, minSdk 26) and JDK 17+.

---

## Testing

The whole domain layer is free of Android imports specifically so it can be tested as
ordinary JVM code:

```bash
./gradlew :app:testDebugUnitTest
```

93 unit tests cover the SMS parser (real bank message formats, and the messages that must
be rejected), merchant normalisation, categorisation precedence, transfer detection, money
formatting, analytics, recurring detection, the health score, the advisor's intents, the
timeline filter, the CSV importer and the PDF extractor (which is exercised against PDFs
generated inside the test, both uncompressed and Flate-compressed).

`SampleLedgerTest` pins the seeded ledger to the figures in the product brief — ₹78,632
spent, ₹16,695 received, ₹61,937 net outflow, groceries at 93% of ₹15,000, food at 70% of
₹6,000 — so a change to the sample data cannot silently drift away from them.

---

## Sample data

A fresh install seeds three months of realistic transactions across four accounts so every
screen has something to show before the first SMS is read. Remove it from
**Settings → Sample data**; anything you added yourself is kept.
