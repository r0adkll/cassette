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
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.PermissionController
import com.r0adkll.cassette.common.compose.PermissionState

/**
 * Runtime permissions for the UI layer. Create it before the activity starts (it registers an
 * activity-result launcher). "Blocked" is: asked before, not granted, and Android no longer shows a
 * rationale, so only the app's system settings can grant it.
 */
class AndroidPermissionController(private val activity: ComponentActivity) : PermissionController {
  private var pending: ((PermissionState) -> Unit)? = null
  private var pendingPermission: AppPermission? = null
  // Lazy: this object is created before the activity has a context.
  private val prefs by lazy { activity.getSharedPreferences("permissions", Context.MODE_PRIVATE) }

  private val launcher: ActivityResultLauncher<Array<String>> =
    activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      val permission = pendingPermission ?: return@registerForActivityResult
      pending?.invoke(state(permission))
      pending = null
      pendingPermission = null
    }

  override fun state(permission: AppPermission): PermissionState {
    val names = permission.manifestNames
    if (names.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED })
      return PermissionState.Granted
    val asked = prefs.getBoolean(permission.name, false)
    val rationale = names.any { activity.shouldShowRequestPermissionRationale(it) }
    return if (asked && !rationale) PermissionState.Blocked else PermissionState.Askable
  }

  override fun request(permission: AppPermission, onResult: (PermissionState) -> Unit) {
    prefs.edit().putBoolean(permission.name, true).apply()
    pending = onResult
    pendingPermission = permission
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
