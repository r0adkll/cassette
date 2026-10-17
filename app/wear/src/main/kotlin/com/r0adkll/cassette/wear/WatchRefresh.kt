package com.r0adkll.cassette.wear

import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What Refresh shows on the watch (decision 0013): the phone does the scanning. */
sealed interface WatchRefresh {
  data object Idle : WatchRefresh

  /** Asked the phone; waiting for it to start. */
  data object Asking : WatchRefresh

  data class Scanning(val secondsLeft: Int) : WatchRefresh

  /** The phone's Bluetooth is off, or Cassette isn't allowed to scan. */
  data object PhoneNotReady : WatchRefresh

  /** No phone with Cassette is reachable. */
  data object NoPhone : WatchRefresh
}

/** A message stays this long before Refresh is offered again. */
private val MESSAGE_FOR = 8.seconds

/** Give up waiting for the phone to start after this long. */
private val ASKING_FOR = 15.seconds

/**
 * Refresh's state from the phone's refresh, when we asked ([askedAt]) and when we found no phone
 * ([noPhoneAt]). The countdown is the watch's: the phone sends when the scan ends, not every
 * second.
 */
fun watchRefresh(
  phone: WearRefresh?,
  askedAt: Instant?,
  noPhoneAt: Instant?,
  now: Instant,
): WatchRefresh {
  val until = phone?.until
  if (phone?.state == WearRefresh.State.Scanning && until != null) {
    val left = until - now.toEpochMilliseconds()
    if (left > 0) return WatchRefresh.Scanning(ceil(left / 1_000.0).toInt())
  }
  if (noPhoneAt != null && now - noPhoneAt < MESSAGE_FOR) return WatchRefresh.NoPhone
  if (
    phone?.state == WearRefresh.State.NotReady &&
      now - Instant.fromEpochMilliseconds(phone.at) < MESSAGE_FOR
  )
    return WatchRefresh.PhoneNotReady
  if (askedAt != null && now - askedAt < ASKING_FOR) {
    val heardSince = phone != null && phone.at >= askedAt.toEpochMilliseconds()
    if (!heardSince) return WatchRefresh.Asking
  }
  return WatchRefresh.Idle
}
