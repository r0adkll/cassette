package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.Sighting
import com.r0adkll.cassette.data.db.CassetteDatabase
import com.r0adkll.cassette.data.db.createInMemoryDriver
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HeardNearbyTest {
  private val driver = createInMemoryDriver()
  private val db = CassetteDatabase(driver)

  @AfterTest fun close() = driver.close()

  @Test
  fun aHubOnNoBikeIsListedFirst() {
    // The tester's bike as their second export left it: the dropper's system and the Quarq.
    db.bikeQueries.insert("Mountain bike", "Mtb", null, Fixtures.DROPPER, 0, 0)
    component(Fixtures.DROPPER.toString(), ComponentKind.DROPPER_POST, Fixtures.DROPPER)
    component(Fixtures.FORK.toString(), ComponentKind.FORK, Fixtures.DROPPER, FORK_ADDRESS)
    component("Quarq AGD60899", ComponentKind.POWER_METER, null)
    db.hubQueries.insert(Fixtures.DROPPER, 1, 0)

    // Frames from their capture log.
    val events =
      listOf(
        wake(
          1_000,
          sighting(
            Fixtures.DROPPER.toString(),
            -64,
            "00 00 01 02 00 04 05 77 1d 03 80 1a",
            "0d 03 ec d7 d2 42 fb 03 84",
          ),
          sighting(
            Fixtures.SHOCK.toString(),
            -62,
            "00 00 01 02 00 04 05 47 1f 03 80 64",
            "0d 03 64 df 69 3c 3d 04 83",
          ),
        ),
        wake(
          2_000,
          sighting(
            Fixtures.SHOCK.toString(),
            -27,
            "00 00 01 02 00 04 05 47 1f 03 80 64 01 01 09 0d 03 64 df 69 3c 3d 04 83 01",
          ),
          sighting(
            FORK_ADDRESS,
            -56,
            "00 00 01 02 00 04 05 ff 1d 03 80 30 02 04 0a 02 37 0b 67 33 32 33 34 66 38 02",
          ),
          Sighting(
            "Quarq AGD60899",
            "POWER_METER",
            2_000,
            -62,
            svc = "e6 00 00 00 00 00 00 00 e3 ed c3 00 07 55 65 0b 0a ff 00 00 00",
          ),
        ),
      )

    assertEquals(
      """
      Heard nearby: 4 devices in 2 background wakes, 1970-01-01T00:00:01Z to 1970-01-01T00:00:02Z
      Not on a bike
        1013571428          hub · Rear shock · model 1085  STATUS 2  -62 to -27 dBm  last 1970-01-01T00:00:02Z
      On bike 1 (Mountain bike)
        1121114092          hub · Dropper post · model 1019  STATUS 1  -64 to -64 dBm  last 1970-01-01T00:00:01Z  its own system
        CA:FC:68:09:2D:96   no serial  STATUS 1  -56 to -56 dBm  last 1970-01-01T00:00:02Z  the Fork's address (1151498210)
        Quarq AGD60899      Power meter · model 7  POWER_METER 1  -62 to -62 dBm  last 1970-01-01T00:00:02Z
      """
        .trimIndent(),
      heardNearbyReport(events, db).trimEnd(),
    )
  }

  @Test
  fun noWakesSaySo() {
    assertEquals(
      "Heard nearby: no background wakes in the capture log.",
      heardNearbyReport(emptyList(), db),
    )
  }

  private fun component(key: String, kind: ComponentKind, hub: Long?, address: String? = null) {
    db.componentQueries.insert(
      1,
      key,
      key.toLongOrNull(),
      kind.name,
      null,
      null,
      null,
      null,
      0,
      0,
      hub,
    )
    if (address != null)
      db.componentQueries.setAddress(address, db.componentQueries.lastInsertedId().executeAsOne())
  }

  private fun wake(at: Long, vararg sightings: Sighting) =
    CaptureEvent.Delivery(at, sightings.map { it.copy(heardAt = at) }, error = 0)

  private fun sighting(key: String, rssi: Int, mfg: String, svc: String? = null) =
    Sighting(key, "STATUS", 0, rssi, mfg, svc)

  private companion object {
    const val FORK_ADDRESS = "CA:FC:68:09:2D:96"
  }
}
