package com.r0adkll.cassette.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.layout.androidImageResource
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.imageResource
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.ColorScheme
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.asLayoutString
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import com.google.common.util.concurrent.ListenableFuture
import com.r0adkll.cassette.bikes.summary.BikeSummary
import com.r0adkll.cassette.bikes.summary.toSummary
import com.r0adkll.cassette.common.compose.theme.DarkCassetteColors
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import com.r0adkll.cassette.surfaces.wear.sync.toOverviews
import com.r0adkll.cassette.wear.R
import com.r0adkll.cassette.wear.WatchRefresh
import com.r0adkll.cassette.wear.WearActivity
import com.r0adkll.cassette.wear.WearApplication
import com.r0adkll.cassette.wear.detailLine
import com.r0adkll.cassette.wear.watchRefresh
import com.r0adkll.cassette.wear.watchRefreshEndsAt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.flow.first

/**
 * The Wear tile (decision 0013): the default bike's name, status icon and headline, its weakest
 * component and when it was seen, and Refresh, which asks the phone to scan. Tiles can't load
 * fonts, so it uses the system's; the colours are ours.
 */
class BikeTileService : TileService() {
  private val graph
    get() = (application as WearApplication).graph

  override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> =
    SuspendToFutureAdapter.launchFuture {
      tileResponse(requestParams)
    }

  /** The status icons, by id; every version of the tile uses the same ones. */
  override fun onTileResourcesRequest(
    requestParams: ResourcesRequest
  ): ListenableFuture<Resources> = SuspendToFutureAdapter.launchFuture {
    Resources.Builder()
      .setVersion(RESOURCES_VERSION)
      .apply {
        StatusTone.entries.forEach {
          addIdToImageMapping(it.resourceId, imageResource(androidImageResource(it.drawable)))
        }
      }
      .build()
  }

  private suspend fun tileResponse(requestParams: TileRequest): Tile {
    val refresh = graph.tileRefresh
    if (requestParams.currentState.lastClickableId == REFRESH && refresh.askedAt == null) {
      refresh.ask()
    }
    val now = graph.clock.now()
    val phoneRefresh = graph.phoneData.refresh.first()
    val bike = graph.phoneData.snapshot.first()?.defaultBike()?.toSummary(now)
    val device = requestParams.deviceConfiguration

    val state = watchRefresh(phoneRefresh, refresh.askedAt, refresh.noPhoneAt, now)
    val endsAt = watchRefreshEndsAt(phoneRefresh, refresh.askedAt, refresh.noPhoneAt, now)
    val entries =
      if (endsAt == null) listOf(timelineEntry(layout(device, bike, state, now)))
      else {
        // Back to Refresh by itself when the scan ends or the message expires, without a request.
        val after = watchRefresh(phoneRefresh, refresh.askedAt, refresh.noPhoneAt, endsAt)
        listOf(
          timelineEntry(layout(device, bike, state, now), validity(now, endsAt)),
          timelineEntry(layout(device, bike, after, endsAt), validity(endsAt, null)),
        )
      }
    return tile(timeline(*entries.toTypedArray()), resourcesVersion = RESOURCES_VERSION)
  }

  private fun layout(
    device: DeviceParameters,
    bike: BikeSummary?,
    refresh: WatchRefresh,
    now: Instant,
  ): LayoutElement =
    materialScope(this, device, allowDynamicTheme = false, defaultColorScheme = SCHEME) {
      val openApp = clickable(launchApp(this@BikeTileService, bike?.bikeId))
      if (bike == null) {
        primaryLayout(
          titleSlot = { text("CASSETTE".layoutString, color = MUTED) },
          mainSlot = {
            text(
              "Open Cassette on your phone to see your bike here.".layoutString,
              typography = Typography.BODY_MEDIUM,
              color = TEXT,
              maxLines = 3,
            )
          },
          onClick = openApp,
        )
      } else {
        val tone = bike.status.tone
        primaryLayout(
          titleSlot = { text(bike.name.uppercase().layoutString, color = MUTED) },
          mainSlot = {
            column(
              icon(tone.resourceId, tintColor = tone.color),
              text(
                bike.status.headline.layoutString,
                typography = Typography.DISPLAY_SMALL,
                color = TEXT,
              ),
              text(
                message(refresh) ?: bike.detailLine.layoutString,
                typography = Typography.BODY_SMALL,
                color = MUTED,
                maxLines = 2,
              ),
            )
          },
          bottomSlot = {
            textEdgeButton(
              onClick = clickable(loadAction(), id = REFRESH),
              colors = ButtonColors(containerColor = TRACK, labelColor = TEXT, iconColor = TEXT),
            ) {
              text(buttonLabel(refresh, now))
            }
          },
          onClick = openApp,
        )
      }
    }

  private fun MaterialScope.message(refresh: WatchRefresh): LayoutString? =
    when (refresh) {
      WatchRefresh.Idle -> null
      WatchRefresh.Asking -> "Asking the phone…"
      is WatchRefresh.Scanning -> "Wake the bike: the phone is listening"
      WatchRefresh.PhoneNotReady -> "Turn on Bluetooth on the phone"
      WatchRefresh.NoPhone -> "Can't reach the phone"
    }?.layoutString

  private fun buttonLabel(refresh: WatchRefresh, now: Instant): LayoutString =
    when (refresh) {
      is WatchRefresh.Scanning -> countdown(now + refresh.secondsLeft.seconds, refresh.secondsLeft)
      WatchRefresh.Asking -> "Asking".layoutString
      else -> "Refresh".layoutString
    }

  /** Counts down on the watch between tile requests, from the scan's end. */
  private fun countdown(end: Instant, secondsLeft: Int): LayoutString {
    val left =
      DynamicInstant.platformTimeWithSecondsPrecision()
        .durationUntil(DynamicInstant.withSecondsPrecision(end.toJavaInstant()))
        .toIntSeconds()
    val clamped = DynamicInt32.onCondition(left.gt(0)).use(left).elseUse(0)
    return DynamicString.constant("0:")
      .concat(clamped.format(DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()))
      .asLayoutString(
        staticValue = "0:${secondsLeft.toString().padStart(2, '0')}",
        layoutConstraint = StringLayoutConstraint.Builder("0:00").build(),
      )
  }

  private fun WearSnapshot.defaultBike() =
    toOverviews().let { bikes ->
      bikes.firstOrNull { it.bike.id.value == defaultBikeId } ?: bikes.firstOrNull()
    }

  /** Both ends set: the renderer reads an unset end as 0, not "forever". */
  private fun validity(from: Instant, until: Instant?) =
    TimelineBuilders.TimeInterval.Builder()
      .setStartMillis(from.toEpochMilliseconds())
      .setEndMillis(until?.toEpochMilliseconds() ?: Long.MAX_VALUE)
      .build()

  companion object {
    private const val REFRESH = "refresh"
    private const val RESOURCES_VERSION = "1"

    private val TEXT = DarkCassetteColors.text.argb
    private val MUTED = DarkCassetteColors.muted.argb
    private val TRACK = DarkCassetteColors.track.argb

    private val SCHEME =
      ColorScheme(
        primary = DarkCassetteColors.ink.argb,
        onPrimary = DarkCassetteColors.onInk.argb,
        onSurface = TEXT,
        onSurfaceVariant = MUTED,
        surfaceContainer = TRACK,
        background = android.graphics.Color.BLACK.argb,
        onBackground = TEXT,
        error = DarkCassetteColors.critical.argb,
      )

    private val StatusTone.color: LayoutColor
      get() = DarkCassetteColors.status(this).argb

    private val StatusTone.resourceId: String
      get() = "status_${name.lowercase()}"

    private val StatusTone.drawable: Int
      get() =
        when (this) {
          StatusTone.Good -> R.drawable.lucide_circle_check
          StatusTone.Low -> R.drawable.lucide_battery_low
          StatusTone.Critical -> R.drawable.lucide_battery_warning
          StatusTone.Stale -> R.drawable.lucide_history
          StatusTone.Unknown -> R.drawable.lucide_circle_help
        }

    /** Opens the app at [bikeId], or at the bike list. */
    fun launchApp(context: Context, bikeId: Long?): ActionBuilders.LaunchAction =
      ActionBuilders.launchAction(
        ComponentName(context, WearActivity::class.java),
        bikeId?.let {
          mapOf(
            WearActivity.EXTRA_BIKE to
              ActionBuilders.AndroidLongExtra.Builder().setValue(it).build()
          )
        } ?: emptyMap(),
      )
  }
}

private val androidx.compose.ui.graphics.Color.argb: LayoutColor
  get() = android.graphics.Color.argb(alpha, red, green, blue).argb
