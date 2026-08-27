package com.coffeeledger.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coffeeledger.app.data.io.DataTransfer
import com.coffeeledger.app.domain.model.Txn
import com.coffeeledger.app.ui.home.HomeScreen
import com.coffeeledger.app.ui.insights.AdvisorScreen
import com.coffeeledger.app.ui.insights.InsightsScreen
import com.coffeeledger.app.ui.nav.BottomDestination
import com.coffeeledger.app.ui.nav.CoffeeBottomBar
import com.coffeeledger.app.ui.nav.Routes
import com.coffeeledger.app.ui.settings.ImportScreen
import com.coffeeledger.app.ui.settings.PermissionState
import com.coffeeledger.app.ui.settings.PrivacyScreen
import com.coffeeledger.app.ui.settings.SettingsScreen
import com.coffeeledger.app.ui.settings.SmsScreen
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors
import com.coffeeledger.app.ui.tracker.TrackerEditScreen
import com.coffeeledger.app.ui.tracker.TrackerScreen
import com.coffeeledger.app.ui.transactions.AddTransactionScreen
import com.coffeeledger.app.ui.transactions.TransactionDetailScreen
import com.coffeeledger.app.ui.transactions.TransactionsScreen
import kotlinx.coroutines.delay

/**
 * The app shell: one navigation host, one bottom bar, and the system integrations that
 * have to live at the Activity level — the SMS permission and the file pickers.
 */
@Composable
fun LedgerApp(
    viewModel: LedgerViewModel,
    onDataErased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    val context = LocalContext.current
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val lastScan by viewModel.lastScan.collectAsStateWithLifecycle()
    val lastImport by viewModel.lastImport.collectAsStateWithLifecycle()

    var smsPermissionGranted by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.READ_SMS))
    }
    var pendingImportAccount by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        smsPermissionGranted = granted[Manifest.permission.READ_SMS] == true
        if (smsPermissionGranted) {
            viewModel.setSmsIngestionEnabled(true)
            viewModel.scanSmsInbox()
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importFile(context.contentResolver, it, pendingImportAccount, isPdf = false) }
    }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importFile(context.contentResolver, it, pendingImportAccount, isPdf = true) }
    }
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.restoreBackup(context.contentResolver, it) }
    }
    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DataTransfer.JSON_MIME),
    ) { uri ->
        uri?.let { viewModel.exportBackup(context.contentResolver, it) }
    }
    val csvSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DataTransfer.CSV_MIME),
    ) { uri ->
        uri?.let { viewModel.exportCsv(context.contentResolver, it) }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in BottomDestination.entries.map { it.route }

    Box(modifier = modifier.fillMaxSize().background(colors.page)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        state = state,
                        onOpenTransaction = { navController.navigate(Routes.transactionDetail(it)) },
                        onSeeAllTransactions = { navController.switchTab(Routes.TRANSACTIONS) },
                        onOpenAdvisor = { navController.navigate(Routes.ADVISOR) },
                        onOpenTracker = { navController.switchTab(Routes.TRACKER) },
                    )
                }

                composable(Routes.TRANSACTIONS) {
                    TransactionsScreen(
                        state = state,
                        onOpenTransaction = { navController.navigate(Routes.transactionDetail(it)) },
                        onAddTransaction = { navController.navigate(Routes.ADD_TRANSACTION) },
                    )
                }

                composable(Routes.TRACKER) {
                    TrackerScreen(
                        state = state,
                        onOpenTracker = { navController.navigate(Routes.trackerEdit(it)) },
                        onNewTracker = { navController.navigate(Routes.TRACKER_NEW) },
                    )
                }

                composable(Routes.INSIGHTS) {
                    InsightsScreen(
                        state = state,
                        onOpenAdvisor = { navController.navigate(Routes.ADVISOR) },
                        onDismissInsight = viewModel::dismissInsight,
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        state = state,
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onOpenSms = { navController.navigate(Routes.SMS) },
                        onOpenImport = { navController.navigate(Routes.IMPORT) },
                        onExportBackup = {
                            backupSaver.launch(DataTransfer.backupFileName(System.currentTimeMillis()))
                        },
                        onExportCsv = {
                            csvSaver.launch(DataTransfer.csvFileName(System.currentTimeMillis()))
                        },
                        onRestoreBackup = { restorePicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        onRemoveSampleData = viewModel::removeSampleData,
                    )
                }

                composable(Routes.TRANSACTION_DETAIL) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    var txn by remember(id) { mutableStateOf<Txn?>(null) }
                    var raw by remember(id) { mutableStateOf<String?>(null) }
                    LaunchedEffect(id, state.snapshot.transactions) {
                        txn = viewModel.transaction(id)
                        raw = viewModel.rawMessage(id)
                    }
                    txn?.let { transaction ->
                        TransactionDetailScreen(
                            txn = transaction,
                            accountLabel = state.snapshot.accounts
                                .firstOrNull { it.id == transaction.accountId }
                                ?.let { "${it.displayName} ${it.maskedLabel}" },
                            rawMessage = raw,
                            categories = viewModel.categoriesFor(transaction.direction),
                            onSave = { updated, learn ->
                                viewModel.updateTransaction(updated, learn)
                                navController.popBackStack()
                            },
                            onDelete = {
                                viewModel.deleteTransaction(it)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                composable(Routes.ADD_TRANSACTION) {
                    AddTransactionScreen(
                        accounts = state.snapshot.accounts,
                        onSave = {
                            viewModel.addManual(it)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.TRACKER_EDIT) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val existing = state.snapshot.trackers.firstOrNull { it.id == id }
                    TrackerEditScreen(
                        existing = existing,
                        accounts = state.snapshot.accounts,
                        onSave = {
                            viewModel.saveTracker(it)
                            navController.popBackStack()
                        },
                        onDelete = existing?.let {
                            { trackerId: String ->
                                viewModel.deleteTracker(trackerId)
                                navController.popBackStack()
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.ADVISOR) {
                    AdvisorScreen(state = state, onBack = { navController.popBackStack() })
                }

                composable(Routes.PRIVACY) {
                    PrivacyScreen(
                        permissions = context.permissionStates(),
                        keystoreSummary = viewModel.keystoreSummary(),
                        transactionCount = state.snapshot.transactions.size,
                        onDeleteEverything = { viewModel.deleteAllData(onDataErased) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.SMS) {
                    SmsScreen(
                        enabled = state.settings.smsIngestionEnabled,
                        permissionGranted = smsPermissionGranted,
                        busy = busy,
                        lastScanAt = state.settings.lastSmsScanAt,
                        lastScan = lastScan,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                            )
                        },
                        onToggle = viewModel::setSmsIngestionEnabled,
                        onScan = viewModel::scanSmsInbox,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.IMPORT) {
                    ImportScreen(
                        accounts = state.snapshot.accounts,
                        busy = busy,
                        report = lastImport,
                        onPickCsv = { accountId ->
                            pendingImportAccount = accountId
                            csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                        },
                        onPickPdf = { accountId ->
                            pendingImportAccount = accountId
                            pdfPicker.launch(arrayOf("application/pdf"))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            if (showBottomBar) {
                CoffeeBottomBar(
                    current = currentRoute,
                    onNavigate = { navController.switchTab(it.route) },
                )
            }
        }

        ToastBar(
            message = toast?.text,
            onDismiss = viewModel::clearToast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showBottomBar) 88.dp else 24.dp),
        )
    }
}

/** A quiet strip of text at the bottom. No colour, no icon, gone in a few seconds. */
@Composable
private fun ToastBar(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = coffeeColors
    LaunchedEffect(message) {
        if (message != null) {
            delay(3_500)
            onDismiss()
        }
    }
    AnimatedVisibility(visible = message != null, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.textPrimary)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(text = message.orEmpty(), style = CoffeeType.Body, color = colors.card)
        }
    }
}

/** Top-level tabs are a single-instance stack, so switching never piles up back entries. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** What the privacy screen lists. Only permissions the app actually declares appear. */
private fun android.content.Context.permissionStates(): List<PermissionState> = listOf(
    PermissionState(
        name = "Read SMS",
        purpose = "Read existing bank messages to build the ledger",
        granted = hasPermission(Manifest.permission.READ_SMS),
    ),
    PermissionState(
        name = "Receive SMS",
        purpose = "Notice new bank messages as they arrive",
        granted = hasPermission(Manifest.permission.RECEIVE_SMS),
    ),
    PermissionState(
        name = "Internet",
        purpose = "Not requested. The app cannot make network connections.",
        granted = false,
    ),
)
