package com.r0adkll.cassette.surfaces.widgetkit

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.core.BatteryReading
import com.r0adkll.cassette.core.Bike
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.BikeType
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.core.ReadMethod
import com.r0adkll.cassette.protocol.BatteryStatus
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WidgetSnapshotTest {
  private val seen = Instant.fromEpochMilliseconds(1_792_000_000_000)

  private fun component(id: Long, kind: ComponentKind, percent: Int, status: BatteryStatus) =
    ComponentWithReading(
      Component(
        id = ComponentId(id),
        bikeId = BikeId(1),
        key = "$id",
        serial = id,
        kind = kind,
        modelId = null,
        deviceType = null,
        firmware = null,
        actuations = null,
        addedAt = seen,
        lastSeenAt = seen,
        removedAt = null,
      ),
      BatteryReading(
        componentId = ComponentId(id),
        at = seen,
        millivolts = null,
        percent = percent,
        reportedStatus = status,
        method = ReadMethod.Advert,
      ),
    )

  private fun road(lastSeenAt: Instant?) =
    BikeOverview(
      bike =
        Bike(
          id = BikeId(1),
          name = "Road bike",
          type = BikeType.Road,
          photoPath = null,
          hubSerial = null,
          createdAt = seen,
          lastSeenAt = lastSeenAt,
          hubReadAt = null,
        ),
      bikeCount = 1,
      components =
        listOf(
          component(10, ComponentKind.REAR_DERAILLEUR, 78, BatteryStatus.OK),
          component(11, ComponentKind.FRONT_DERAILLEUR, 11, BatteryStatus.LOW),
        ),
    )

  @Test
  fun wordsTheBikeNowAndOnceItGoesStale() {
    val now = seen + 2.minutes
    val bike = widgetSnapshot(listOf(road(seen)), BikeId(1), now).bikes.single()

    assertEquals("Low", bike.current.tone)
    assertEquals("LOW", bike.current.word)
    assertEquals("Charge soon.", bike.current.headline)
    assertEquals("Front derailleur at 11%.", bike.current.summary)
    assertEquals(11L, bike.current.weakestId)
    val fd = bike.current.components.single { it.id == 11L }
    assertEquals("FD", fd.code)
    assertEquals("11%", fd.value)
    assertEquals("Low", fd.tone)
    assertEquals("FRONT_DERAILLEUR", fd.kind)

    // Stale starts a millisecond past 14 days, as isStale says.
    assertEquals((seen + 14.days + 1.milliseconds).toEpochMilliseconds(), bike.staleAt)
    val stale = bike.stale!!
    assertEquals("Stale", stale.tone)
    assertEquals("OLD", stale.word)
    assertEquals("Not seen in 14 days.", stale.headline)
    assertEquals(listOf("Stale", "Stale"), stale.components.map { it.tone })
  }

  @Test
  fun aStaleOrUnseenBikeHasNothingToComeLater() {
    val old = widgetSnapshot(listOf(road(seen)), null, seen + 16.days).bikes.single()
    assertEquals("Not seen in 16 days.", old.current.headline)
    assertNull(old.staleAt)
    assertNull(old.stale)

    val never = widgetSnapshot(listOf(road(null)), null, seen).bikes.single()
    assertNull(never.lastSeenAt)
    assertNull(never.staleAt)
  }

  @Test
  fun roundTripsAndToleratesANewerApp() {
    val snapshot = widgetSnapshot(listOf(road(seen)), BikeId(1), seen)
    assertEquals(snapshot, WidgetCodec.decode(WidgetCodec.encode(snapshot)))
    val newer = WidgetCodec.encode(snapshot).replaceFirst("{", "{\"colour\":\"red\",")
    assertEquals(snapshot, WidgetCodec.decode(newer))
    assertNull(WidgetCodec.decode("not json"))
  }
}
