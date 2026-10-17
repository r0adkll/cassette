package com.r0adkll.cassette.wear

import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WatchRefreshTest {
  private val now = Instant.fromEpochMilliseconds(1_792_000_000_000)
  private val ms = now.toEpochMilliseconds()

  @Test
  fun idleUntilAsked() {
    assertEquals(WatchRefresh.Idle, watchRefresh(null, null, null, now))
  }

  @Test
  fun askingUntilThePhoneAnswersOrGivesUp() {
    assertEquals(WatchRefresh.Asking, watchRefresh(null, now - 3.seconds, null, now))
    // An answer from before we asked doesn't count.
    val old = WearRefresh(WearRefresh.State.Idle, at = ms - 60_000)
    assertEquals(WatchRefresh.Asking, watchRefresh(old, now - 3.seconds, null, now))
    assertEquals(WatchRefresh.Idle, watchRefresh(null, now - 20.seconds, null, now))
  }

  @Test
  fun countsDownToTheScansEnd() {
    val scanning = WearRefresh(WearRefresh.State.Scanning, until = ms + 41_200, at = ms - 18_800)
    assertEquals(WatchRefresh.Scanning(42), watchRefresh(scanning, now - 20.seconds, null, now))
    // A scan that should have ended (the phone went away) stops counting.
    val ended = scanning.copy(until = ms - 1)
    assertEquals(WatchRefresh.Idle, watchRefresh(ended, null, null, now))
  }

  @Test
  fun messagesShowBrieflyThenRefreshIsOfferedAgain() {
    assertEquals(WatchRefresh.NoPhone, watchRefresh(null, null, now - 2.seconds, now))
    assertEquals(WatchRefresh.Idle, watchRefresh(null, null, now - 10.seconds, now))
    val notReady = WearRefresh(WearRefresh.State.NotReady, at = ms - 2_000)
    assertEquals(WatchRefresh.PhoneNotReady, watchRefresh(notReady, now - 3.seconds, null, now))
    assertEquals(WatchRefresh.Idle, watchRefresh(notReady, null, null, now + 10.seconds))
  }

  @Test
  fun eachStateKnowsWhenItLapses() {
    assertEquals(null, watchRefreshEndsAt(null, null, null, now))
    assertEquals(now + 12.seconds, watchRefreshEndsAt(null, now - 3.seconds, null, now))
    val scanning = WearRefresh(WearRefresh.State.Scanning, until = ms + 41_000, at = ms - 19_000)
    assertEquals(now + 41.seconds, watchRefreshEndsAt(scanning, null, null, now))
    assertEquals(now + 6.seconds, watchRefreshEndsAt(null, null, now - 2.seconds, now))
    // What follows is idle.
    val end = watchRefreshEndsAt(scanning, null, null, now)!!
    assertEquals(WatchRefresh.Idle, watchRefresh(scanning, null, null, end))
  }
}
