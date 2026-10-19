package com.r0adkll.cassette.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.lifecycleScope
import com.r0adkll.cassette.app.shared.CassetteRoot
import com.r0adkll.cassette.common.compose.AppInfo
import com.r0adkll.cassette.common.screens.DeepLinks
import com.r0adkll.drop.ui.DropUpdateSheet
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val app
    get() = application as CassetteApplication

  private val permissions = AndroidPermissionController(this)

  /** Links from notifications and widgets that arrive while the activity is running. */
  private val newLinks = Channel<List<Screen>>(Channel.CONFLATED)

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    lifecycleScope.launch { app.graph.captureController.ensureBackgroundCapture("app opened") }
    val updates = app.updates
    if (savedInstanceState == null) updates.check()

    // A restored activity keeps its back stack; only a fresh start follows the launching link.
    val launchLink = if (savedInstanceState == null) intent.link() else null
    val pkg = packageManager.getPackageInfo(packageName, 0)
    val appInfo = AppInfo(version = pkg.versionName.orEmpty(), build = "${pkg.longVersionCode}")
    setContent {
      CassetteRoot(
        circuit = app.graph.circuit,
        settings = app.graph.settings,
        bikes = app.graph.bikeRepository,
        permissions = permissions,
        updates = updates,
        appInfo = appInfo,
        launchLink = launchLink,
        newLinks = newLinks.receiveAsFlow(),
        systemBars = { dark, _ ->
          // The status and navigation bar icons follow the app's theme, not the phone's.
          DisposableEffect(dark) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
          }
        },
        overlay = {
          if (updates.sheetOpen)
            DropUpdateSheet(drop = updates.drop, onDismiss = updates::closeSheet)
        },
      )
    }
  }

  override fun onStop() {
    super.onStop()
    // Leaving the app, not rotating it: the bike screen picks a new status title (decision 0015).
    if (!isChangingConfigurations) app.graph.statusTitles.appBackgrounded()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.link()?.let(newLinks::trySend)
  }

  private fun Intent.link(): List<Screen>? = data?.toString()?.let(DeepLinks::parse)
}
