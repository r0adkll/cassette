package com.r0adkll.cassette.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.PermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Runtime permissions for the UI layer. Create it before the activity starts (it registers an
 * activity-result launcher). "Blocked" is: asked before, not granted, and Android no longer shows a
 * rationale, so only the app's system settings can grant it. States are re-read on every resume, so
 * a change made in system settings shows up when the user comes back.
 */
class AndroidPermissionController(private val activity: ComponentActivity) : PermissionController {
  // Lazy: this object is created before the activity has a context.
  private val prefs by lazy { activity.getSharedPreferences("permissions", Context.MODE_PRIVATE) }

  override val states by lazy { MutableStateFlow(readAll()) }

  private val launcher: ActivityResultLauncher<Array<String>> =
    activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      states.value = readAll()
    }

  init {
    activity.lifecycle.addObserver(
      object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
          states.value = readAll()
        }
      }
    )
  }

  private fun readAll() = AppPermission.entries.associateWith(::state)

  private fun state(permission: AppPermission): PermissionState {
    val names = permission.manifestNames
    if (names.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED })
      return PermissionState.Granted
    val asked = prefs.getBoolean(permission.name, false)
    val rationale = names.any { activity.shouldShowRequestPermissionRationale(it) }
    return if (asked && !rationale) PermissionState.Blocked else PermissionState.Askable
  }

  override fun request(permission: AppPermission) {
    prefs.edit().putBoolean(permission.name, true).apply()
    launcher.launch(permission.manifestNames)
  }

  override fun openSettings() {
    activity.startActivity(
      Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", activity.packageName, null),
      )
    )
  }

  private val AppPermission.manifestNames: Array<String>
    get() =
      when (this) {
        AppPermission.Nearby ->
          arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        AppPermission.Notifications -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
      }
}
