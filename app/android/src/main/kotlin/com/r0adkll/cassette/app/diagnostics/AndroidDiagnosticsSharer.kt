package com.r0adkll.cassette.app.diagnostics

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.FileProvider
import app.cash.sqldelight.db.SqlDriver
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.capture.api.DiagnosticsSharer
import com.r0adkll.cassette.capture.api.PhoneBattery
import com.r0adkll.cassette.settings.api.CassetteSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Zips the capture log, a consistent copy of the database and a device summary into the cache and
 * opens the share sheet. The user picks where it goes; nothing is uploaded.
 */
@ContributesBinding(AppScope::class)
@Inject
class AndroidDiagnosticsSharer(
  private val application: Application,
  private val driver: SqlDriver,
  private val log: CaptureLog,
  private val diagnostics: Diagnostics,
  private val settings: CassetteSettings,
  private val clock: Clock,
) : DiagnosticsSharer {

  override suspend fun share(): Boolean {
    val zip =
      withContext(Dispatchers.IO) {
        runCatching {
          val dir = File(application.cacheDir, "diagnostics").apply { mkdirs() }
          dir.listFiles()?.forEach { it.delete() }
          // VACUUM INTO writes a consistent snapshot even with the WAL in use.
          val dbCopy = File(dir, "cassette.db")
          driver.execute(null, "VACUUM INTO '${dbCopy.absolutePath}'", 0)
          val zip = File(dir, "cassette-diagnostics-${clock.now().toEpochMilliseconds()}.zip")
          ZipOutputStream(zip.outputStream()).use { out ->
            out.entry("info.txt", info().toByteArray())
            out.entry("capture-log.jsonl", log.export().toByteArray())
            out.entry("cassette.db", dbCopy.readBytes())
          }
          dbCopy.delete()
          zip
        }
          .getOrNull()
      } ?: return false

    val uri = FileProvider.getUriForFile(application, "${application.packageName}.diagnostics", zip)
    val send =
      Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "Cassette diagnostics")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    application.startActivity(
      Intent.createChooser(send, "Share diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    return true
  }

  private suspend fun info(): String {
    val app = application.packageManager.getPackageInfo(application.packageName, 0)
    val bluetooth = application.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
    val health = settings.captureHealth.first()
    return buildString {
      appendLine("Cassette ${app.versionName} (${application.packageName})")
      appendLine("Created ${clock.now()}")
      appendLine(
        "Device ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
      )
      appendLine("Bluetooth ${if (bluetooth == true) "on" else "off or unavailable"}")
      appendLine(
        "Background capture ${settings.backgroundCapture.first()}, profile ${settings.scanProfile.first()}"
      )
      appendLine("Pinned bike ${settings.pinnedBike.first()?.value ?: "none"}")
      appendLine("Arm: ${health.armResult} at ${health.armedAt}")
      appendLine("Background wakes ${health.deliveries}, last ${health.lastDeliveryAt}")
      appendLine()
      // A slip here shouldn't cost the rest of the export.
      append(runCatching { diagnostics.heardNearby() }.getOrElse { "Heard nearby: failed, $it" })
    }
  }

  private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(bytes)
    closeEntry()
  }
}

@ContributesBinding(AppScope::class)
@Inject
class AndroidPhoneBattery(private val application: Application) : PhoneBattery {
  override fun percent(): Int? =
    application
      .getSystemService(BatteryManager::class.java)
      ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
      ?.takeIf { it in 0..100 }
}
