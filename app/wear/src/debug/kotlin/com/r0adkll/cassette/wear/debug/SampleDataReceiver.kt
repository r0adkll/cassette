package com.r0adkll.cassette.wear.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.r0adkll.cassette.surfaces.wear.sync.WearBike
import com.r0adkll.cassette.surfaces.wear.sync.WearCodec
import com.r0adkll.cassette.surfaces.wear.sync.WearComponent
import com.r0adkll.cassette.surfaces.wear.sync.WearPaths
import com.r0adkll.cassette.surfaces.wear.sync.WearReading
import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Writes a sample snapshot to the watch's own Data Layer node, so the app, tile and complications
 * can be checked without a paired phone. A phone's snapshot, when there is one, wins.
 *
 * `--es state` good (default), low, critical, stale, one, empty, scanning, notready or clear.
 */
class SampleDataReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val state = intent.getStringExtra("state") ?: "good"
    val data = Wearable.getDataClient(context)
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val now = Clock.System.now()
        when (state) {
          "clear" -> {
            data.deleteDataItems(uri(WearPaths.SNAPSHOT)).await()
            data.deleteDataItems(uri(WearPaths.REFRESH)).await()
          }
          "scanning" ->
            put(
              data,
              WearPaths.REFRESH,
              WearCodec.encode(
                WearRefresh(
                  WearRefresh.State.Scanning,
                  until = (now + 60.seconds).toEpochMilliseconds(),
                  at = now.toEpochMilliseconds(),
                )
              ),
            )
          "notready" ->
            put(
              data,
              WearPaths.REFRESH,
              WearCodec.encode(
                WearRefresh(WearRefresh.State.NotReady, at = now.toEpochMilliseconds())
              ),
            )
          else -> put(data, WearPaths.SNAPSHOT, WearCodec.encode(sample(state, now)))
        }
      } finally {
        pending.finish()
      }
    }
  }

  private suspend fun put(
    data: com.google.android.gms.wearable.DataClient,
    path: String,
    bytes: ByteArray,
  ) {
    data.putDataItem(PutDataRequest.create(path).setData(bytes).setUrgent()).await()
  }

  private fun uri(path: String) =
    android.net.Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()

  /** The validation bike's readings (as in the phone's sample bike), plus a second bike. */
  private fun sample(state: String, now: Instant): WearSnapshot {
    val seen = if (state == "stale") now - 20.days else now - 2.minutes
    val (fdMv, fdStatus) =
      when (state) {
        "low" -> 7_400 to "LOW"
        "critical" -> 7_000 to "CRITICAL"
        else -> 8_050 to "OK"
      }
    fun reading(mv: Int?, percent: Int?, status: String?, method: String = "Hub") =
      WearReading(seen.toEpochMilliseconds(), mv, percent, status, method)
    val road =
      WearBike(
        id = 1,
        name = "Road bike",
        type = "Road",
        lastSeenAt = seen.toEpochMilliseconds(),
        hubReadAt = seen.toEpochMilliseconds(),
        components =
          listOf(
            WearComponent(
              10,
              "rd",
              "REAR_DERAILLEUR",
              "2.55.8",
              reading(7_870, 78, "OK", "Advert"),
            ),
            WearComponent(11, "fd", "FRONT_DERAILLEUR", "2.55.8", reading(fdMv, null, fdStatus)),
            WearComponent(12, "l", "LEFT_SHIFTER", "1.4.2", reading(2_910, null, "OK")),
            WearComponent(13, "r", "RIGHT_SHIFTER", "1.4.2", reading(2_890, null, "OK")),
            WearComponent(14, "pm", "POWER_METER", null, reading(null, 85, null, "Gatt")),
          ),
      )
    val gravel =
      WearBike(
        id = 2,
        name = "Gravel bike",
        type = "Gravel",
        lastSeenAt = (now - 3.days).toEpochMilliseconds(),
        hubReadAt = null,
        components =
          listOf(
            WearComponent(20, "rd2", "REAR_DERAILLEUR", null, reading(7_950, 88, "OK", "Advert")),
            WearComponent(21, "r2", "RIGHT_SHIFTER", null, reading(2_880, null, "OK")),
          ),
      )
    val bikes =
      when (state) {
        "empty" -> emptyList()
        "one" -> listOf(road)
        else -> listOf(road, gravel)
      }
    return WearSnapshot(defaultBikeId = bikes.firstOrNull()?.id, bikes = bikes)
  }
}
