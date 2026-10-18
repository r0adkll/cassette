package com.r0adkll.cassette.app.ios

import app.cash.sqldelight.db.SqlDriver
import com.r0adkll.cassette.bikes.api.HomeSurfaces
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.DiagnosticsSharer
import com.r0adkll.cassette.capture.api.PhoneBattery
import com.r0adkll.cassette.data.ble.IosCentral
import com.r0adkll.cassette.settings.api.CassetteSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/** No widgets or Control Center control yet (decision 0016): the bike screen offers neither. */
@ContributesBinding(AppScope::class)
@Inject
class IosHomeSurfaces : HomeSurfaces {
  override fun canOfferWidget() = false

  override fun requestWidget() = Unit

  override fun canOfferTile() = false

  override fun requestTile() = Unit
}

@ContributesBinding(AppScope::class)
@Inject
class IosPhoneBattery : PhoneBattery {
  override fun percent(): Int? {
    val device = UIDevice.currentDevice
    device.batteryMonitoringEnabled = true
    return (device.batteryLevel * 100).toInt().takeIf { it in 0..100 }
  }
}

/**
 * Writes the capture log, a consistent copy of the database and a device summary to a temporary
 * folder and opens the share sheet with them. The user picks where they go; nothing is uploaded.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@ContributesBinding(AppScope::class)
@Inject
class IosDiagnosticsSharer(
  private val driver: SqlDriver,
  private val log: CaptureLog,
  private val settings: CassetteSettings,
  private val central: IosCentral,
  private val clock: Clock,
) : DiagnosticsSharer {
  override suspend fun share(): Boolean {
    val files =
      withContext(Dispatchers.IO) {
        runCatching {
          val files = NSFileManager.defaultManager
          val dir = NSTemporaryDirectory() + "diagnostics-${clock.now().toEpochMilliseconds()}"
          files.createDirectoryAtPath(dir, true, null, null)
          // VACUUM INTO writes a consistent snapshot even with the WAL in use.
          val db = "$dir/cassette.db"
          driver.execute(null, "VACUUM INTO '$db'", 0)
          val info = "$dir/info.txt"
          val capture = "$dir/capture-log.jsonl"
          check(info().write(info) && log.export().write(capture))
          listOf(info, capture, db)
        }
          .getOrNull()
      } ?: return false

    return withContext(Dispatchers.Main) {
      val top = topViewController() ?: return@withContext false
      val sheet =
        UIActivityViewController(
          files.map { NSURL.fileURLWithPath(it) },
          applicationActivities = null,
        )
      // iPads show the sheet as a popover, which needs an anchor.
      sheet.popoverPresentationController?.let { popover ->
        popover.sourceView = top.view
        top.view.bounds.useContents {
          popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
        }
      }
      top.presentViewController(sheet, animated = true, completion = null)
      true
    }
  }

  private suspend fun info(): String {
    val bundle = NSBundle.mainBundle
    val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString")
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion")
    val device = UIDevice.currentDevice
    val health = settings.captureHealth.first()
    return buildString {
      appendLine("Cassette $version ($build, ${bundle.bundleIdentifier})")
      appendLine("Created ${clock.now()}")
      appendLine("Device ${device.model}, ${device.systemName} ${device.systemVersion}")
      appendLine("Bluetooth ${central.state.value}")
      appendLine("Background capture ${settings.backgroundCapture.first()} (iOS sets the cadence)")
      appendLine("Pinned bike ${settings.pinnedBike.first()?.value ?: "none"}")
      appendLine("Arm: ${health.armResult} at ${health.armedAt}")
      appendLine("Background wakes ${health.deliveries}, last ${health.lastDeliveryAt}")
    }
  }

  private fun String.write(path: String): Boolean =
    NSString.create(string = this).writeToFile(path, true, NSUTF8StringEncoding, null)

  private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
  }
}
