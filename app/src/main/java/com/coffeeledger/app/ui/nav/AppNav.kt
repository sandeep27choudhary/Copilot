package com.coffeeledger.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coffeeledger.app.ui.components.HairLine
import com.coffeeledger.app.ui.theme.CoffeeType
import com.coffeeledger.app.ui.theme.coffeeColors

/** Every place the app can be. Detail routes carry their id in the path. */
object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val TRACKER = "tracker"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"

    const val TRANSACTION_DETAIL = "transaction/{id}"
    const val TRACKER_EDIT = "tracker-edit/{id}"
    const val TRACKER_NEW = "tracker-edit/new"
    const val ADD_TRANSACTION = "add-transaction"
    const val ADVISOR = "advisor"
    const val PRIVACY = "privacy"
    const val SMS = "sms"
    const val IMPORT = "import"

    fun transactionDetail(id: String) = "transaction/$id"
    fun trackerEdit(id: String) = "tracker-edit/$id"
}

/** The five top-level destinations, in the order they appear in the bar. */
enum class BottomDestination(val route: String, val label: String) {
    HOME(Routes.HOME, "Home"),
    TRANSACTIONS(Routes.TRANSACTIONS, "Transactions"),
    TRACKER(Routes.TRACKER, "Tracker"),
    INSIGHTS(Routes.INSIGHTS, "Insights"),
    SETTINGS(Routes.SETTINGS, "Settings"),
}

/**
 * A text-only bottom bar.
 *
 * Five labels and a small dot for the current tab. Icons would need a second icon pack and
 * would add colour and weight the rest of the design spends its time avoiding.
 */
@Composable
fun CoffeeBottomBar(
    current: String?,
    onNavigate: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = coffeeColors
    Column(modifier = modifier.fillMaxWidth().background(colors.page)) {
        HairLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomDestination.entries.forEach { destination ->
                val selected = current == destination.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .clickable { onNavigate(destination) }
                        .padding(vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.accent else Color.Transparent),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = destination.label,
                        style = CoffeeType.Caption,
                        color = if (selected) colors.textPrimary else colors.textTertiary,
                    )
                }
            }
        }
    }
}
