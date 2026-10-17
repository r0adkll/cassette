package com.r0adkll.cassette.wear

import com.r0adkll.cassette.surfaces.wear.sync.WearBike
import com.r0adkll.cassette.surfaces.wear.sync.WearComponent
import com.r0adkll.cassette.surfaces.wear.sync.WearReading
import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.wear.data.PhoneData
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

val NOW: Instant = Instant.fromEpochMilliseconds(1_792_000_000_000)

class FixedClock(var now: Instant = NOW) : Clock {
  override fun now(): Instant = now
}

class FakePhoneData(snapshot: WearSnapshot? = null) : PhoneData {
  override val snapshot = MutableStateFlow(snapshot)
  override val refresh = MutableStateFlow<WearRefresh?>(null)
  var reachable = true
  var requests = 0

  override suspend fun requestRefresh(): Boolean {
    requests++
    return reachable
  }
}

fun reading(mv: Int?, percent: Int?, status: String?, at: Instant = NOW) =
  WearReading(at.toEpochMilliseconds(), mv, percent, status, "Hub")

fun bike(
  id: Long,
  name: String,
  vararg components: WearComponent,
  seen: Instant = NOW,
) = WearBike(id, name, "Road", seen.toEpochMilliseconds(), null, components.toList())

fun component(id: Long, kind: String, reading: WearReading?) =
  WearComponent(id, "k$id", kind, null, reading)

val road =
  bike(
    1,
    "Road bike",
    component(10, "REAR_DERAILLEUR", reading(7_870, 78, "OK")),
    component(11, "FRONT_DERAILLEUR", reading(7_400, null, "LOW")),
  )

val gravel = bike(2, "Gravel bike", component(20, "REAR_DERAILLEUR", reading(7_950, 88, "OK")))
