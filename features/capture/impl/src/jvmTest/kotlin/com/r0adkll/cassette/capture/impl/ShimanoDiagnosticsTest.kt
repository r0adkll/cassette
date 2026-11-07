package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.GattKind
import com.r0adkll.cassette.capture.api.GattOutcome
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.BATTERY_LEVEL_CHARACTERISTIC
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.createInMemoryDriver
import com.r0adkll.cassette.protocol.ShimanoAdvert
import com.r0adkll.cassette.protocol.ShimanoAdvertParser
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/** Decision 0029: Shimano adverts in the capture log, and the probe's open reads. */
class ShimanoDiagnosticsTest {
  private val driver = createInMemoryDriver()
  private val db = CassetteDatabase(driver)
  private val dispatchers =
    CassetteDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)
  private val clock =
    object : Clock {
      override fun now() = Instant.fromEpochMilliseconds(1_792_200_000_000)
    }
  private val scanner = FakeScanner()
  private val gatt = FakeGatt()
  private val log = InMemoryCaptureLog()
  private val diagnostics =
    DefaultDiagnostics(scanner, gatt, CaptureRecorder(db, dispatchers), db, log, dispatchers, clock)

  @AfterTest fun close() = driver.close()

  @Test
  fun logsFirstAdvertAndChangesAtMostEveryFewSeconds() = runTest {
    listOf(
        advert(at = 0, raw = "aa"),
        advert(at = 1_000, raw = "aa"), // the same bytes: not logged
        advert(at = 2_000, raw = "bb"), // changed, but too soon
        advert(at = 6_000, raw = "cc"), // changed, five seconds on
        advert(at = 6_500, raw = "aa", address = OTHER), // another derailleur
      )
      .forEach { scanner.adverts.emit(it) }

    val heard = diagnostics.liveAdverts().take(5).toList()

    assertEquals(5, heard.size)
    val logged = log.list.value.filterIsInstance<CaptureEvent.Shimano>()
    assertEquals(listOf("aa", "cc", "aa"), logged.map { it.raw })
    assertEquals(listOf(ADDRESS, ADDRESS, OTHER), logged.map { it.address })
    assertEquals(listOf("18ef"), logged.first().services)
  }

  @Test
  fun probeReadsBatteryAndDeviceInformation() = runTest {
    gatt.values =
      mapOf(
        BATTERY_LEVEL_CHARACTERISTIC to byteArrayOf(87),
        MODEL to "RD-R9250\u0000".encodeToByteArray(),
      )

    val probe = diagnostics.probeShimano(ADDRESS)

    assertEquals(87, probe.batteryPercent)
    assertEquals(mapOf("model" to "RD-R9250"), probe.deviceInfo)
    assertEquals(listOf("battery 87 %", "model RD-R9250"), probe.lines)
    val read = log.list.value.filterIsInstance<CaptureEvent.Gatt>().single()
    assertEquals(GattKind.ShimanoProbe, read.kind)
    assertEquals(GattOutcome.Ok, read.outcome)
    assertEquals("57", read.hex)
    assertEquals("battery 87 %; model RD-R9250", read.detail)
  }

  @Test
  fun probeSaysWhyItFailed() = runTest {
    gatt.fail = true

    val probe = diagnostics.probeShimano(ADDRESS)

    assertNull(probe.batteryPercent)
    assertEquals(listOf("read failed: timed out after 12s while connecting"), probe.lines)
    assertEquals(
      GattOutcome.Failed,
      log.list.value.filterIsInstance<CaptureEvent.Gatt>().single().outcome,
    )
  }

  private fun advert(at: Long, raw: String, address: String = ADDRESS) =
    ShimanoAdvert(
      address = address,
      name = "RDR9250",
      rssi = -60,
      timestampMillis = at,
      manufacturerHex = null,
      services = listOf(ShimanoAdvertParser.SBI_SERVICE),
      serviceData = emptyMap(),
      rawHex = raw,
    )

  private companion object {
    const val ADDRESS = "AA:BB:CC:DD:EE:10"
    const val OTHER = "AA:BB:CC:DD:EE:11"
    const val MODEL = "00002a24-0000-1000-8000-00805f9b34fb"
  }
}
