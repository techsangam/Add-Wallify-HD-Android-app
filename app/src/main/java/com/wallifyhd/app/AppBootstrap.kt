package com.wallifyhd.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.wallifyhd.app.core.AppContainer
import com.wallifyhd.app.core.WallifyViewModelFactory
import com.wallifyhd.app.ui.WallifyApp
import com.wallifyhd.app.ui.theme.WallifyTheme

class WallifyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class WallifyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WallifyApplication
        setContent {
            val factory = remember(app.container) {
                WallifyViewModelFactory(app.container)
            }

            WallifyTheme {
                WallifyApp(viewModelFactory = factory)
            }
        }
    }
}
