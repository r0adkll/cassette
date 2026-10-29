package com.r0adkll.cassette.capture.impl

import app.cash.turbine.test
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.data.ble.AdvertScanner
import com.r0adkll.cassette.protocol.AxsReading
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.protocol.FrameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

class LoggedScanTest {
  private val log = InMemoryCaptureLog()
  private var now = Instant.fromEpochMilliseconds(0)
  private val clock =
    object : Clock {
      override fun now() = this@LoggedScanTest.now
    }

  private val scans
    get() = log.list.value.filterIsInstance<CaptureEvent.Scan>().map { it.detail to it.ok }

  @Test
  fun aScanSaysHowLongItRanAndWhatItHeard() = runTest {
    val scanner = FakeScanner()
    scanner.loggedLive("add bike", log, clock).test {
      scanner.adverts.emit(advert("1121114092"))
      scanner.adverts.emit(advert("1121114092"))
      scanner.adverts.emit(advert("1013571428"))
      repeat(3) { awaitItem() }
      now = Instant.fromEpochMilliseconds(12_000)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(
      listOf("started" to true, "stopped after 12 s, 3 adverts from 2 devices" to true),
      scans,
    )
    assertEquals("add bike", log.list.value.filterIsInstance<CaptureEvent.Scan>().last().purpose)
  }

  @Test
  fun aFailedScanIsLoggedWithItsReason() = runTest {
    val scanner =
      object : AdvertScanner by FakeScanner() {
        override fun live(): Flow<AxsReading> = flow {
          throw IllegalStateException("Scan failed with error 6 (scanning too frequently)")
        }
      }
    scanner.loggedLive("refresh", log, clock).test { awaitError() }
    assertEquals(
      listOf(
        "started" to true,
        "failed after 0 s, 0 adverts from 0 devices: " +
          "Scan failed with error 6 (scanning too frequently)" to false,
      ),
      scans,
    )
  }

  @Test
  fun aScanThatHeardNothingIsNotOk() = runTest {
    FakeScanner().loggedLive("add bike", log, clock).test { cancelAndIgnoreRemainingEvents() }
    assertEquals(false, scans.last().second)
  }

  private fun advert(key: String) =
    AxsReading(
      key = key,
      serial = key.toLong(),
      address = "AA:$key",
      name = null,
      rssi = -60,
      timestampMillis = 0,
      frame = FrameType.STATUS,
      kind = ComponentKind.UNKNOWN,
      battery = null,
      firmware = null,
      manufacturerHex = null,
      serviceDataHex = null,
    )
}
