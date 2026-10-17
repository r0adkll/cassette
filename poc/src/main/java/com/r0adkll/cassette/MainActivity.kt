package com.r0adkll.cassette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.r0adkll.cassette.theme.CassetteTheme
import com.r0adkll.cassette.ui.MainScreen
import com.r0adkll.cassette.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CassetteApp

        enableEdgeToEdge()
        setContent {
            CassetteTheme {
                MainScreen(
                    viewModel = viewModel { MainViewModel(app) },
                    hasPermissions = {
                        app.scanner.hasScanPermission() && app.gattReader.hasConnectPermission()
                    },
                    isBluetoothEnabled = { app.scanner.isBluetoothEnabled },
                )
            }
        }
    }
}
