package com.coffeeledger.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.ui.components.BackRow
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.components.PaperCard
import com.coffeeledger.app.ui.components.QuietButton
import com.coffeeledger.app.ui.components.SectionLabel
import com.coffeeledger.app.ui.components.Tag
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/** One permission and whether the user has granted it. */
data class PermissionState(val name: String, val purpose: String, val granted: Boolean)

/**
 * The privacy dashboard.
 *
 * It states the guarantee, lists exactly which permissions are live, and puts the erase
 * control at the bottom where it belongs. Every claim here corresponds to something in the
 * code rather than to a policy document.
 */
@Composable
fun PrivacyScreen(
    permissions: List<PermissionState>,
    keystoreSummary: String,
    transactionCount: Int,
    onDeleteEverything: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    var confirmations by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
    ) {
        item {
            BackRow(label = "Settings", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Your financial data stays on your device.",
                style = CoffeeType.LargeAmount,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This app has no account, no server and no analytics. It does not ask for network access.",
                style = CoffeeType.Body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(28.dp))
        }

        item {
            PaperCard {
                GuaranteeRow("Transactions stored locally", "In an encrypted SQLCipher database in this app's private storage.")
                HairLine()
                GuaranteeRow("SMS processed locally", "Messages are parsed on the device. The text is never uploaded.")
                HairLine()
                GuaranteeRow("Analytics processed locally", "Trends, categories and the health score are computed here.")
                HairLine()
                GuaranteeRow("Financial advisor processed locally", "Questions are matched to your own data. No cloud model is called.")
                HairLine()
                GuaranteeRow("No financial data sharing by default", "Data leaves only through an export you start yourself.")
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            SectionLabel("Encryption")
            Spacer(Modifier.height(10.dp))
            PaperCard {
                Text(text = keystoreSummary, style = CoffeeType.Body, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The database passphrase is 32 random bytes sealed with a key that cannot be exported from the Keystore. A copy of the database file taken off this device cannot be read.",
                    style = CoffeeType.Caption,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        item {
            SectionLabel("Permissions")
            Spacer(Modifier.height(10.dp))
            PaperCard {
                permissions.forEachIndexed { index, permission ->
                    if (index > 0) HairLine()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = permission.name,
                                style = CoffeeType.Body,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = permission.purpose,
                                style = CoffeeType.Caption,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Tag(text = if (permission.granted) "Granted" else "Not granted")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "The app declares no internet permission at all, so it cannot send your data anywhere even if it tried.",
                style = CoffeeType.Caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(28.dp))
        }

        item {
            SectionLabel("Erase")
            Spacer(Modifier.height(10.dp))
            PaperCard {
                Text(
                    text = "Delete all financial data",
                    style = CoffeeType.Title,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Removes all $transactionCount transactions, accounts, trackers, rules and insights, then destroys the Keystore key so the database file left behind cannot be decrypted. This cannot be undone.",
                    style = CoffeeType.Body,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                QuietButton(
                    text = when (confirmations) {
                        0 -> "Delete everything"
                        1 -> "Are you sure? Tap again"
                        else -> "Tap once more to erase"
                    },
                    tint = colors.caution,
                    onClick = {
                        if (confirmations >= 2) onDeleteEverything() else confirmations++
                    },
                )
            }
        }
    }
}

@Composable
private fun GuaranteeRow(title: String, detail: String) {
    val colors = coffeeColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = "✓", style = CoffeeType.Title, color = colors.positive)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = CoffeeType.Body, color = colors.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(text = detail, style = CoffeeType.Caption, color = colors.textSecondary)
        }
    }
}
