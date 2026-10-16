package com.r0adkll.cassette.cdm

import com.r0adkll.cassette.ridetest.GattOutcome
import com.r0adkll.cassette.ridetest.PresenceReadPath
import com.r0adkll.cassette.ridetest.RideEvent
import com.r0adkll.cassette.ridetest.alwaysLogged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceTimelineTest {
  @Test
  fun pairsAppearedEventsWithTheReadTheyStarted() {
    val events =
      listOf(
        presence(at = 1_000, id = 1, EVENT_BLE_APPEARED),
        read(at = 4_500, id = 1, appearedAt = 1_000),
        presence(at = 60_000, id = 1, EVENT_BLE_DISAPPEARED),
        presence(at = 70_000, id = 2, EVENT_BLE_APPEARED),
        // A read for a different appearance of the same association isn't paired with this one.
        read(at = 90_000, id = 2, appearedAt = 50_000),
      )

    val rows = presenceTimeline(events)

    assertEquals(listOf(70_000L, 60_000L, 1_000L), rows.map { it.event.at })
    assertNull(rows[0].read)
    assertNull(rows[1].read)
    assertEquals(3_500L, rows[2].latencyMillis)
  }

  @Test
  fun keepsTheNewestEvents() {
    val events = (1..30).map { presence(at = it * 1_000L, id = 1, EVENT_BLE_APPEARED) }

    val rows = presenceTimeline(events, limit = 20)

    assertEquals(20, rows.size)
    assertEquals(30_000L, rows.first().event.at)
    assertEquals(11_000L, rows.last().event.at)
  }

  @Test
  fun tracksHubAddressesOncePerHubAndOnChange() {
    val tracker = HubAddressTracker()

    assertEquals(HubAddressTracker.Change("123", "AA:BB:CC:DD:EE:FF", null), tracker.observe("123", "aa:bb:cc:dd:ee:ff"))
    assertNull(tracker.observe("123", "AA:BB:CC:DD:EE:FF"))
    assertEquals(HubAddressTracker.Change("456", "11:22:33:44:55:66", null), tracker.observe("456", "11:22:33:44:55:66"))
    assertEquals(HubAddressTracker.Change("123", "AA:BB:CC:DD:EE:00", "AA:BB:CC:DD:EE:FF"), tracker.observe("123", "AA:BB:CC:DD:EE:00"))
  }

  @Test
  fun matchesAssociationsByAddressIgnoringCase() {
    val associations = listOf(1 to null, 2 to "aa:bb:cc:dd:ee:ff")

    assertEquals(2, matchAssociation("AA:BB:CC:DD:EE:FF", associations))
    assertNull(matchAssociation("AA:BB:CC:DD:EE:00", associations))
  }

  @Test
  fun onlyCdmEventsAreAlwaysLogged() {
    assertTrue(presence(at = 0, id = 1, EVENT_BLE_APPEARED).alwaysLogged)
    assertTrue(RideEvent.Cdm(at = 0, action = "observe").alwaysLogged)
    assertFalse(RideEvent.BudgetExceeded(at = 0).alwaysLogged)
  }

  private fun presence(at: Long, id: Int, event: Int) =
    RideEvent.Presence(at = at, associationId = id, address = "AA:BB:CC:DD:EE:FF", event = event, eventName = presenceEventName(event))

  private fun read(at: Long, id: Int, appearedAt: Long) =
    RideEvent.PresenceRead(
      at = at,
      associationId = id,
      address = "AA:BB:CC:DD:EE:FF",
      key = "123",
      path = PresenceReadPath.BOUND_SERVICE,
      appearedAt = appearedAt,
      durationMillis = 2_000,
      outcome = GattOutcome.OK,
    )
}
