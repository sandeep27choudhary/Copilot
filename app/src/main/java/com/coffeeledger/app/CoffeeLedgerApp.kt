package com.coffeeledger.app

import android.app.Application
import com.coffeeledger.app.di.AppContainer

class CoffeeLedgerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Rebuilds the container after the user erases everything. */
    fun resetContainer() {
        container.destroyAllStorage()
        container = AppContainer(this)
    }
}
