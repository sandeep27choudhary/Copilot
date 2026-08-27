package com.coffeeledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.coffeeledger.app.ui.LedgerApp
import com.coffeeledger.app.ui.LedgerViewModel
import com.coffeeledger.app.ui.theme.CoffeeLedgerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LedgerViewModel by viewModels {
        LedgerViewModel.factory((application as CoffeeLedgerApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CoffeeLedgerTheme {
                LedgerApp(
                    viewModel = viewModel,
                    // Erasing destroys the database and its key, so the process is
                    // restarted rather than left holding handles to files that are gone.
                    onDataErased = {
                        (application as CoffeeLedgerApp).resetContainer()
                        finishAffinity()
                    },
                )
            }
        }
    }
}
