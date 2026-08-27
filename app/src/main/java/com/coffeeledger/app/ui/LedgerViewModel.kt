package com.coffeeledger.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coffeeledger.app.data.io.DataTransfer
import com.coffeeledger.app.data.repo.LedgerRepository
import com.coffeeledger.app.data.repo.LedgerSnapshot
import com.coffeeledger.app.data.repo.Settings
import com.coffeeledger.app.data.repo.SettingsRepository
import com.coffeeledger.app.di.AppContainer
import com.coffeeledger.app.domain.analytics.AnalyticsEngine
import com.coffeeledger.app.domain.analytics.CategoryTotal
import com.coffeeledger.app.domain.analytics.FinancialHealth
import com.coffeeledger.app.domain.analytics.HealthScore
import com.coffeeledger.app.domain.analytics.Insight
import com.coffeeledger.app.domain.analytics.MerchantTotal
import com.coffeeledger.app.domain.analytics.MonthPoint
import com.coffeeledger.app.domain.analytics.PeriodSummary
import com.coffeeledger.app.domain.analytics.RecurringPayment
import com.coffeeledger.app.domain.analytics.TimeRanges
import com.coffeeledger.app.domain.importer.CsvStatementParser
import com.coffeeledger.app.domain.importer.ImportException
import com.coffeeledger.app.domain.importer.ImportReport
import com.coffeeledger.app.domain.importer.PdfStatementParser
import com.coffeeledger.app.domain.importer.PdfTextExtractor
import com.coffeeledger.app.domain.model.Category
import com.coffeeledger.app.domain.model.SourceType
import com.coffeeledger.app.domain.model.Tracker
import com.coffeeledger.app.domain.model.TrackerProgress
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.sms.SmsScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the five main screens read, computed once per change to the ledger. */
data class LedgerUiState(
    val loading: Boolean = true,
    val now: Long = System.currentTimeMillis(),
    val snapshot: LedgerSnapshot = LedgerSnapshot(emptyList(), emptyList(), emptyList(), emptyList()),
    val settings: Settings = Settings(),
    val thisMonth: PeriodSummary? = null,
    val lastMonth: PeriodSummary? = null,
    val trackers: List<TrackerProgress> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val health: HealthScore = HealthScore(0, "Not enough data", emptyList()),
    val trend: List<MonthPoint> = emptyList(),
    val topCategories: List<CategoryTotal> = emptyList(),
    val topMerchants: List<MerchantTotal> = emptyList(),
    val recurring: List<RecurringPayment> = emptyList(),
) {
    val monthLabel: String get() = thisMonth?.range?.label ?: ""
    val hasData: Boolean get() = snapshot.transactions.isNotEmpty()
    /** Entries the SMS parser was unsure about and that are worth a glance. */
    val needsReviewCount: Int get() = snapshot.transactions.count { it.needsReview }
    /** The unfiltered monthly cap, which the dashboard meter tracks. */
    val monthlyTracker: TrackerProgress?
        get() = trackers.firstOrNull {
            it.tracker.kind == com.coffeeledger.app.domain.model.TrackerKind.SPENDING_LIMIT &&
                it.tracker.categoryIds.isEmpty() && it.tracker.merchantNames.isEmpty()
        }
}

/** A short message shown once, then cleared. */
data class Toast(val text: String, val id: Long = System.currentTimeMillis())

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _lastScan = MutableStateFlow<SmsScanReport?>(null)
    val lastScan: StateFlow<SmsScanReport?> = _lastScan

    private val _lastImport = MutableStateFlow<ImportReport?>(null)
    val lastImport: StateFlow<ImportReport?> = _lastImport

    val state: StateFlow<LedgerUiState> =
        combine(repository.observeSnapshot(), settingsRepository.observe()) { snapshot, settings ->
            buildState(snapshot, settings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

    init {
        viewModelScope.launch {
            repository.seedSampleDataIfEmpty(System.currentTimeMillis())
            repository.ensureStarterTrackers()
        }
    }

    private suspend fun buildState(snapshot: LedgerSnapshot, settings: Settings): LedgerUiState {
        val now = System.currentTimeMillis()
        return withContext(Dispatchers.Default) {
            val txns = snapshot.transactions
            val thisMonth = AnalyticsEngine.summarize(txns, TimeRanges.currentMonth(now))
            val lastMonth = AnalyticsEngine.summarize(txns, TimeRanges.previousMonth(now))
            val progress = AnalyticsEngine.progressOf(snapshot.trackers, txns, now)
            LedgerUiState(
                loading = false,
                now = now,
                snapshot = snapshot,
                settings = settings,
                thisMonth = thisMonth,
                lastMonth = lastMonth,
                trackers = progress,
                insights = repository.refreshInsights(snapshot, now),
                health = FinancialHealth.evaluate(txns, progress, now),
                trend = AnalyticsEngine.monthlyTrend(txns, MONTHS_OF_TREND, now),
                topCategories = AnalyticsEngine.categoryTotals(txns, TimeRanges.currentMonth(now)),
                topMerchants = AnalyticsEngine.merchantTotals(txns, TimeRanges.currentMonth(now)),
                recurring = AnalyticsEngine.recurringPayments(txns),
            )
        }
    }

    // -------------------------------------------------------------- SMS

    /** Reads the inbox, parses it locally and stores whatever is new. */
    fun scanSmsInbox() {
        launchBusy {
            val report = withContext(Dispatchers.IO) { container.smsInboxReader.scan() }
            val added = repository.ingestParsed(report.parsed)
            settingsRepository.setLastSmsScanAt(System.currentTimeMillis())
            _lastScan.value = report
            _toast.value = Toast(
                "Read ${report.messagesRead} messages, recognised ${report.recognised}, added $added new.",
            )
        }
    }

    fun setSmsIngestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSmsIngestionEnabled(enabled)
            if (!enabled) _lastScan.value = null
        }
    }

    // ----------------------------------------------------------- import

    fun importFile(resolver: ContentResolver, uri: Uri, accountId: String?, isPdf: Boolean) {
        launchBusy {
            try {
                val report = withContext(Dispatchers.IO) {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw ImportException("That file could not be opened.")
                    if (isPdf) {
                        PdfStatementParser.parse(PdfTextExtractor.extract(bytes))
                    } else {
                        CsvStatementParser.parse(String(bytes, Charsets.UTF_8))
                    }
                }
                val added = repository.ingestImported(
                    rows = report.rows,
                    accountId = accountId,
                    sourceType = if (isPdf) SourceType.PDF else SourceType.CSV,
                    sourceApp = if (isPdf) "PDF statement" else "CSV import",
                )
                _lastImport.value = report
                _toast.value = Toast("Read ${report.importedCount} rows, added $added new.")
            } catch (error: ImportException) {
                _toast.value = Toast(error.message ?: "That file could not be read.")
            } catch (error: Exception) {
                _toast.value = Toast("That file could not be read.")
            }
        }
    }

    // ----------------------------------------------------------- export

    fun exportBackup(resolver: ContentResolver, uri: Uri) {
        launchBusy {
            val snapshot = state.value.snapshot
            val json = DataTransfer.toJson(snapshot, System.currentTimeMillis())
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            _toast.value = Toast("Backup written with ${snapshot.transactions.size} transactions.")
        }
    }

    fun exportCsv(resolver: ContentResolver, uri: Uri) {
        launchBusy {
            val csv = DataTransfer.toCsv(state.value.snapshot.transactions)
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            }
            _toast.value = Toast("Exported ${state.value.snapshot.transactions.size} transactions.")
        }
    }

    fun restoreBackup(resolver: ContentResolver, uri: Uri) {
        launchBusy {
            try {
                val contents = withContext(Dispatchers.IO) {
                    val text = resolver.openInputStream(uri)?.use { String(it.readBytes()) }
                        ?: throw ImportException("That file could not be opened.")
                    DataTransfer.fromJson(text)
                }
                contents.accounts.forEach { repository.saveAccount(it) }
                contents.trackers.forEach { repository.saveTracker(it) }
                contents.transactions.forEach { repository.addManual(it) }
                _toast.value = Toast("Restored ${contents.transactions.size} transactions.")
            } catch (error: Exception) {
                _toast.value = Toast(error.message ?: "That backup could not be read.")
            }
        }
    }

    // ------------------------------------------------------------ edits

    fun updateTransaction(txn: Txn, learnRule: Boolean) {
        viewModelScope.launch {
            repository.updateTransaction(txn, learnRule)
            _toast.value = Toast(
                if (learnRule) "Saved. ${txn.merchant} will use ${txn.category.label} from now on."
                else "Saved.",
            )
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _toast.value = Toast("Transaction deleted.")
        }
    }

    fun addManual(txn: Txn) {
        viewModelScope.launch {
            repository.addManual(txn)
            _toast.value = Toast("Added ${txn.merchant}.")
        }
    }

    suspend fun rawMessage(id: String): String? = repository.rawMessage(id)

    suspend fun transaction(id: String): Txn? = repository.transaction(id)

    // --------------------------------------------------------- trackers

    fun saveTracker(tracker: Tracker) {
        viewModelScope.launch {
            repository.saveTracker(tracker)
            _toast.value = Toast("${tracker.title} saved.")
        }
    }

    fun deleteTracker(id: String) {
        viewModelScope.launch {
            repository.deleteTracker(id)
            _toast.value = Toast("Tracker removed.")
        }
    }

    suspend fun nextTrackerSortOrder(): Int = repository.nextTrackerSortOrder()

    fun dismissInsight(id: String) {
        viewModelScope.launch { repository.dismissInsight(id) }
    }

    // ---------------------------------------------------------- privacy

    fun removeSampleData() {
        launchBusy {
            val removed = repository.removeSampleData()
            settingsRepository.setSampleDataPresent(false)
            _toast.value = Toast("Removed $removed sample transactions.")
        }
    }

    /** Erases every table and the key that decrypts them. */
    fun deleteAllData(onDone: () -> Unit) {
        launchBusy {
            repository.deleteAllData()
            onDone()
        }
    }

    fun keystoreSummary(): String = container.keyManager.protectionSummary()

    fun categoriesFor(direction: com.coffeeledger.app.domain.model.Direction): List<Category> =
        repository.categoriesFor(direction)

    fun clearToast() {
        _toast.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        private const val MONTHS_OF_TREND = 6

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LedgerViewModel(
                        container.ledgerRepository,
                        container.settingsRepository,
                        container,
                    ) as T
            }
    }
}
