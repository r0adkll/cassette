package com.r0adkll.cassette.surfaces.widgetkit

import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.ComponentWithReading
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.RefreshRun
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The road bike through a refresh, as iteration 16 drew it. */
class RefreshActivityTest {
  private val started = Instant.fromEpochMilliseconds(1_792_400_000_000)
  private val before = started - 2.hours

  private data class Part(
    val id: Long,
    val kind: ComponentKind,
    val percent: Int?,
    val millivolts: Int?,
    val status: BatteryStatus,
  )

  private val parts =
    listOf(
      Part(1, ComponentKind.FRONT_DERAILLEUR, 11, null, BatteryStatus.LOW),
      Part(2, ComponentKind.REAR_DERAILLEUR, 78, null, BatteryStatus.OK),
      Part(3, ComponentKind.LEFT_SHIFTER, null, 2_910, BatteryStatus.OK),
      Part(4, ComponentKind.RIGHT_SHIFTER, null, 2_890, BatteryStatus.OK),
      Part(5, ComponentKind.POWER_METER, 85, null, BatteryStatus.OK),
    )

  /** The road bike with [answered] read during the refresh and the rest from two hours before. */
  private fun road(vararg answered: ComponentKind, id: Long = 1, name: String = "Road bike") =
    BikeOverview(
      bike =
        Bike(
          id = BikeId(id),
          name = name,
          type = BikeType.Road,
          photoPath = null,
          hubSerial = null,
          createdAt = before,
          lastSeenAt = if (answered.isEmpty()) before else started + 5.seconds,
          hubReadAt = null,
        ),
      bikeCount = 1,
      components =
        parts.map { part ->
          val at = if (part.kind in answered) started + 5.seconds else before
          val componentId = ComponentId(id * 10 + part.id)
          ComponentWithReading(
            Component(
              id = componentId,
              bikeId = BikeId(id),
              key = "${componentId.value}",
              serial = componentId.value,
              kind = part.kind,
              modelId = null,
              deviceType = null,
              firmware = null,
              actuations = null,
              addedAt = before,
              lastSeenAt = at,
              removedAt = null,
            ),
            BatteryReading(
              componentId = componentId,
              at = at,
              millivolts = part.millivolts,
              percent = part.percent,
              reportedStatus = part.status,
              method = ReadMethod.Advert,
            ),
          )
        },
    )

  private fun scanning(left: Int, heard: Int = 0, total: Int = 60) =
    RefreshRun(started, RefreshProgress.Scanning(left, heard, total))

  private fun done(seconds: Int, stopped: Boolean = false) =
    RefreshRun(
      started,
      RefreshProgress.Done(5, hubRead = true, stopped = stopped),
      started + seconds.seconds,
    )

  private fun state(
    run: RefreshRun,
    vararg bikes: BikeOverview,
    default: Long? = 1,
    ready: Boolean = true,
  ) = refreshActivityState(run, bikes.toList(), default?.let(::BikeId), ready)

  @Test
  fun listensUntilSomethingAnswers() {
    val state = state(scanning(52), road())!!

    assertEquals(RefreshPhase.Listening, state.phase)
    assertEquals("ROAD BIKE · LISTENING", state.label)
    assertEquals("Shift once to wake the bike.", state.line)
    assertEquals(0, state.heard)
    assertEquals((started + 60.seconds).toEpochMilliseconds(), state.endsAt)
    assertEquals(listOf("FD", "RD", "L", "R", "PM"), state.components.map { it.code })
    assertTrue(state.components.none { it.answered })
    assertFalse(state.ended)
  }

  @Test
  fun componentsArriveOneByOne() {
    val two =
      state(
        scanning(41, 2),
        road(ComponentKind.FRONT_DERAILLEUR, ComponentKind.REAR_DERAILLEUR),
      )!!
    assertEquals(RefreshPhase.Reading, two.phase)
    assertEquals("ROAD BIKE · READING", two.label)
    assertEquals("Waiting for L, R and PM.", two.line)
    assertEquals("2 of 5.", two.headline)
    assertEquals(2, two.heard)
    assertEquals(listOf("11%", "78%", "2.91", "2.89", "85%"), two.components.map { it.value })
    assertEquals(listOf(true, true, false, false, false), two.components.map { it.answered })
    assertEquals(listOf("Low", "Good"), two.components.take(2).map { it.tone })

    val four =
      state(
        scanning(33, 4),
        road(
          ComponentKind.FRONT_DERAILLEUR,
          ComponentKind.REAR_DERAILLEUR,
          ComponentKind.LEFT_SHIFTER,
          ComponentKind.RIGHT_SHIFTER,
        ),
      )!!
    assertEquals("Waiting for PM. Spin the crank.", four.line)
  }

  @Test
  fun addSixtySecondsMovesTheEnd() {
    val state = state(scanning(100, total = 120), road())!!
    assertEquals(120, state.totalSeconds)
    assertEquals((started + 120.seconds).toEpochMilliseconds(), state.endsAt)
  }

  @Test
  fun doneSaysWhatItFoundAndStaysFiveMinutes() {
    val state = state(done(31), road(*ComponentKind.entries.toTypedArray()))!!

    assertEquals(RefreshPhase.Done, state.phase)
    assertEquals("ROAD BIKE · READ IN 31 S", state.label)
    assertEquals("Charge soon. Front derailleur at 11%.", state.line)
    assertEquals("Charge soon.", state.headline)
    assertEquals("Front derailleur at 11%.", state.detail)
    assertEquals("Low", state.tone)
    assertEquals(5, state.heard)
    assertTrue(state.ended)
    assertEquals(300, state.dismissAfterSeconds)
  }

  @Test
  fun aScanThatEndsShortIsPartialOrSilent() {
    val partial =
      state(
        done(60),
        road(
          ComponentKind.FRONT_DERAILLEUR,
          ComponentKind.REAR_DERAILLEUR,
          ComponentKind.LEFT_SHIFTER,
          ComponentKind.RIGHT_SHIFTER,
        ),
      )!!
    assertEquals(RefreshPhase.Partial, partial.phase)
    assertEquals("ROAD BIKE · 4 OF 5 READ", partial.label)
    assertEquals("No answer from PM.", partial.line)

    val silent = state(done(60), road())!!
    assertEquals(RefreshPhase.Silent, silent.phase)
    assertEquals("ROAD BIKE · NO ANSWER", silent.label)
  }

  @Test
  fun bluetoothOffEndsIt() {
    val state = state(scanning(30, 2), road(ComponentKind.REAR_DERAILLEUR), ready = false)!!
    assertEquals(RefreshPhase.NotReady, state.phase)
    assertEquals("ROAD BIKE · NOT READY", state.label)
    assertEquals("Bluetooth is off. Turn it on, then refresh.", state.line)
    assertTrue(state.ended)
  }

  @Test
  fun stoppedGoesAtOnce() {
    val state = state(done(12, stopped = true), road())!!
    assertEquals(RefreshPhase.Stopped, state.phase)
    assertTrue(state.ended)
    assertEquals(0, state.dismissAfterSeconds)
  }

  @Test
  fun followsTheBikeThatAnswers() {
    val gravel = road(id = 2, name = "Gravel bike")
    // Nothing yet: the default bike.
    assertEquals(2L, state(scanning(50), road(), gravel, default = 2)?.bikeId)
    // The road bike answers, so the activity moves to it.
    val answering = road(ComponentKind.REAR_DERAILLEUR)
    assertEquals(1L, state(scanning(40, 1), answering, gravel, default = 2)?.bikeId)
  }

  @Test
  fun noBikeNoActivity() {
    assertNull(state(scanning(50), default = null))
  }

  @Test
  fun encodesForTheExtension() {
    val json = RefreshActivityCodec.encode(state(scanning(52), road())!!)
    assertTrue(json.contains("\"phase\":\"Listening\""), json)
    assertTrue(json.contains("\"code\":\"FD\""), json)
  }
}
