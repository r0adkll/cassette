package com.r0adkll.cassette.wear.tile

import com.r0adkll.cassette.wear.data.PhoneData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The tile's side of Refresh: when it asked the phone, and when it found no phone. Tiles are
 * stateless between requests, so this lives in the app; losing it with the process only drops a
 * message.
 */
@SingleIn(AppScope::class)
@Inject
class TileRefresh(private val phone: PhoneData, private val clock: Clock) {
  var askedAt: Instant? = null
    private set

  var noPhoneAt: Instant? = null
    private set

  suspend fun ask() {
    askedAt = clock.now()
    noPhoneAt = null
    if (!phone.requestRefresh()) {
      askedAt = null
      noPhoneAt = clock.now()
    }
  }
}
