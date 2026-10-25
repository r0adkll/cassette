package com.r0adkll.cassette.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.bikes.summary.BikeSummary
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.bikes.summary.componentKindOrNull
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.common.compose.theme.DarkCassetteColors
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.protocol.ComponentKind
import com.r0adkll.cassette.surfaces.wear.sync.bike
import com.r0adkll.cassette.wear.R
import com.r0adkll.cassette.wear.WearActivity
import com.r0adkll.cassette.wear.WearApplication
import kotlinx.coroutines.flow.first

/**
 * A bike's complications (decision 0013): the weakest component as a ranged value ("78", "RD") and
 * the status as short text ("GOOD", "FD LOW"), each with the status icon. Tapping opens the bike.
 * Each one picks its bike and component when it goes on a face ([ComplicationConfigActivity],
 * decision 0027); a picked component shows as itself in both shapes ("92", "92%", "PM").
 */
class BikeComplicationService : SuspendingComplicationDataSourceService() {
  private val graph
    get() = (application as WearApplication).graph

  override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
    val pick = graph.complicationPicks.get(request.complicationInstanceId) ?: ComplicationPick()
    val bike = graph.phoneData.snapshot.first()?.bike(pick.bikeId)?.toSummary(graph.clock.now())
    return data(request.complicationType, bike, componentKindOrNull(pick.kind))
  }

  override fun onComplicationDeactivated(complicationInstanceId: Int) {
    graph.complicationPicks.remove(complicationInstanceId)
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? =
    data(type, PREVIEW, picked = null)

  private fun data(
    type: ComplicationType,
    bike: BikeSummary?,
    picked: ComponentKind?,
  ): ComplicationData? {
    if (bike == null) return NoDataComplicationData()
    val model = bike.complication(picked)
    val description = text(model.description)
    val icon =
      MonochromaticImage.Builder(Icon.createWithResource(this, model.tone.drawable)).build()
    val tap = openBike(this, bike.bikeId)
    return when (type) {
      ComplicationType.RANGED_VALUE -> {
        val level = model.level ?: return NoDataComplicationData()
        RangedValueComplicationData.Builder(level * 100, 0f, 100f, description)
          .setText(text(model.value.orEmpty()))
          .setTitle(text(model.code.orEmpty()))
          .setMonochromaticImage(icon)
          // Charge now, low, good: the face may use it, or its own colours.
          .setColorRamp(ColorRamp(intArrayOf(CRITICAL, LOW, GOOD), interpolated = false))
          .setTapAction(tap)
          .build()
      }
      ComplicationType.SHORT_TEXT ->
        ShortTextComplicationData.Builder(text(model.status), description)
          .setTitle(model.statusTitle?.let(::text))
          .setMonochromaticImage(icon)
          .setTapAction(tap)
          .build()
      else -> null
    }
  }

  private fun text(value: String) = PlainComplicationText.Builder(value).build()

  companion object {
    private val GOOD = DarkCassetteColors.good.toArgb()
    private val LOW = DarkCassetteColors.low.toArgb()
    private val CRITICAL = DarkCassetteColors.critical.toArgb()

    /** For the watch face's picker: the validation bike, all good. */
    private val PREVIEW =
      BikeSummary(
        bikeId = 0,
        name = "Road bike",
        bikeCount = 1,
        status = BikeStatus(StatusTone.Good, "All good.", "", "Seen 2 min ago"),
        components =
          listOf(ComponentRowModel(0, "RD", "Rear derailleur", "", "78%", 0.78f, StatusTone.Good)),
      )

    private val StatusTone.drawable: Int
      get() =
        when (this) {
          StatusTone.Good -> R.drawable.lucide_circle_check
          StatusTone.Low -> R.drawable.lucide_battery_low
          StatusTone.Critical -> R.drawable.lucide_battery_warning
          StatusTone.Stale -> R.drawable.lucide_history
          StatusTone.Unknown -> R.drawable.lucide_circle_help
        }

    /** Request code: 4,000,000 + bike id (ledger in CLAUDE.md). */
    private fun openBike(context: Context, bikeId: Long): PendingIntent =
      PendingIntent.getActivity(
        context,
        (4_000_000 + bikeId).toInt(),
        Intent(context, WearActivity::class.java)
          .putExtra(WearActivity.EXTRA_BIKE, bikeId)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    /** Re-renders one complication, after its pick changed. */
    fun update(context: Context, complicationId: Int) {
      ComplicationDataSourceUpdateRequester.create(
          context,
          ComponentName(context, BikeComplicationService::class.java),
        )
        .requestUpdate(complicationId)
    }

    /** Re-renders every complication from this data source. */
    fun update(context: Context) {
      ComplicationDataSourceUpdateRequester.create(
          context,
          ComponentName(context, BikeComplicationService::class.java),
        )
        .requestUpdateAll()
    }
  }
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
  android.graphics.Color.argb(alpha, red, green, blue)
