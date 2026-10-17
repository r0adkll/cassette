package com.r0adkll.cassette.surfaces.wear.sync

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
import kotlin.time.Instant

class WearSnapshotTest {
  private val seen = Instant.fromEpochMilliseconds(1_792_000_000_000)

  private val road =
    BikeOverview(
      bike =
        Bike(
          id = BikeId(1),
          name = "Road bike",
          type = BikeType.Road,
          photoPath = "/data/photo.jpg",
          hubSerial = 123,
          createdAt = seen,
          lastSeenAt = seen,
          hubReadAt = seen,
        ),
      bikeCount = 1,
      components =
        listOf(
          ComponentWithReading(
            Component(
              id = ComponentId(10),
              bikeId = BikeId(1),
              key = "456",
              serial = 456,
              kind = ComponentKind.REAR_DERAILLEUR,
              modelId = 1,
              deviceType = 2,
              firmware = "1.2.3",
              actuations = 1000,
              addedAt = seen,
              lastSeenAt = seen,
              removedAt = null,
            ),
            BatteryReading(
              componentId = ComponentId(10),
              at = seen,
              millivolts = 7_900,
              percent = 78,
              reportedStatus = BatteryStatus.OK,
              method = ReadMethod.Advert,
            ),
          ),
          ComponentWithReading(
            Component(
              id = ComponentId(11),
              bikeId = BikeId(1),
              key = "789",
              serial = 789,
              kind = ComponentKind.LEFT_SHIFTER,
              modelId = null,
              deviceType = null,
              firmware = null,
              actuations = null,
              addedAt = seen,
              lastSeenAt = null,
              removedAt = null,
            ),
            null,
          ),
        ),
    )

  @Test
  fun roundTripKeepsWhatTheWatchShows() {
    val bytes = WearCodec.encode(wearSnapshot(listOf(road), BikeId(1)))
    val snapshot = WearCodec.decodeSnapshot(bytes)!!
    assertEquals(1L, snapshot.defaultBikeId)

    val overview = snapshot.toOverviews().single()
    assertEquals("Road bike", overview.bike.name)
    assertEquals(BikeType.Road, overview.bike.type)
    assertEquals(seen, overview.bike.lastSeenAt)
    assertNull(overview.bike.photoPath)
    assertEquals(1, overview.bikeCount)

    val (rd, left) = overview.components
    assertEquals(ComponentKind.REAR_DERAILLEUR, rd.component.kind)
    assertEquals("1.2.3", rd.component.firmware)
    assertEquals(road.components[0].latest, rd.latest)
    assertEquals(ComponentKind.LEFT_SHIFTER, left.component.kind)
    assertNull(left.latest)
  }

  @Test
  fun unknownNamesAndFieldsFromANewerPhoneAreTolerated() {
    val json =
      """
      {"version":2,"defaultBikeId":null,"colour":"red","bikes":[{"id":1,"name":"E-MTB",
      "type":"Ebike","lastSeenAt":null,"hubReadAt":null,"components":[{"id":2,"key":"k",
      "kind":"MOTOR","firmware":null,"latest":{"at":0,"millivolts":null,"percent":50,
      "reportedStatus":"SUPERCHARGED","method":"Cloud"}}]}]}
      """
    val overview = WearCodec.decodeSnapshot(json.encodeToByteArray())!!.toOverviews().single()
    assertEquals(BikeType.Other, overview.bike.type)
    val component = overview.components.single()
    assertEquals(ComponentKind.UNKNOWN, component.component.kind)
    assertEquals(BatteryStatus.UNKNOWN, component.latest!!.reportedStatus)
    assertEquals(ReadMethod.Advert, component.latest!!.method)
  }

  @Test
  fun garbageIsNotASnapshot() {
    assertNull(WearCodec.decodeSnapshot("not json".encodeToByteArray()))
    assertNull(WearCodec.decodeRefresh(byteArrayOf(0, 1, 2)))
  }

  @Test
  fun refreshRoundTrip() {
    val refresh = WearRefresh(WearRefresh.State.Scanning, until = 60_000, at = 0)
    assertEquals(refresh, WearCodec.decodeRefresh(WearCodec.encode(refresh)))
  }
}
