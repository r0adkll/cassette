package com.r0adkll.cassette.diagnostics.ui

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.Diagnostics
import com.r0adkll.cassette.capture.api.HubDump
import com.r0adkll.cassette.capture.api.ShimanoProbe
import com.r0adkll.cassette.common.screens.Di2CheckScreen
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.protocol.Advert
import com.r0adkll.cassette.protocol.ShimanoAdvert
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class Di2CheckPresenterTest {
  private val now = Instant.fromEpochMilliseconds(1_792_200_000_000)
  private val adverts = MutableSharedFlow<Advert>(replay = 10)
  private val probed = mutableListOf<String>()
  private var shared = 0
  private val log = RecordingLog()
  private val diagnostics =
    object : Diagnostics {
      override fun liveAdverts(): Flow<Advert> = adverts

      override suspend fun probeShimano(address: String): ShimanoProbe {
        probed += address
        return ShimanoProbe(address, now, 87, mapOf("model" to "RD-R9250"), listOf("180f"), null)
      }

      override suspend fun readHubTables(bikeId: BikeId): List<HubDump> = emptyList()

      override suspend fun heardNearby() = ""
    }
  private val presenter =
    Di2CheckPresenter(
      FakeNavigator(Di2CheckScreen),
      diagnostics,
      log,
      { true.also { shared++ } },
      object : Clock {
        override fun now() = this@Di2CheckPresenterTest.now
      },
    )

  @Test
  fun readsTheClosestDerailleurThenAsksAboutShifting() = runTest {
    presenter.test {
      assertNull(awaitItem().derailleur)

      adverts.emit(advert("AA:01", rssi = -80))
      adverts.emit(advert("AA:02", rssi = -55))
      var state = expectMostRecentItem()
      assertEquals(HeardDi2("AA:02", "RDR9250", -55, others = 1), state.derailleur)

      state.eventSink(Di2CheckEvent.ReadBattery)
      state = expectMostRecentItem()
      val probe = assertIs<Di2Read.Done>(state.read).probe
      assertEquals(87, probe.batteryPercent)
      assertEquals(listOf("AA:02"), probed)

      // A stronger advert after the read doesn't move the check off the derailleur it read.
      adverts.emit(advert("AA:01", rssi = -40))
      assertEquals("AA:02", expectMostRecentItem().derailleur?.address)

      state.eventSink(Di2CheckEvent.Shifted(false))
      state = expectMostRecentItem()
      assertEquals(false, state.shifted)

      state.eventSink(Di2CheckEvent.Share)
      expectMostRecentItem()
      assertEquals(1, shared)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(
      listOf(
        "opened",
        "heard RDR9250 AA:01",
        "heard RDR9250 AA:02",
        "didn't shift normally after the read",
        "shared",
      ),
      log.events.value.filterIsInstance<CaptureEvent.Di2Check>().map { it.detail },
    )
  }

  private fun advert(address: String, rssi: Int) =
    ShimanoAdvert(address, "RDR9250", rssi, 0L, null, emptyList(), emptyMap(), null)

  private class RecordingLog : CaptureLog {
    override val events = MutableStateFlow(emptyList<CaptureEvent>())

    override suspend fun append(event: CaptureEvent) {
      events.value = events.value + event
    }

    override suspend fun clear() {
      events.value = emptyList()
    }

    override suspend fun export() = ""
  }
}
