package com.r0adkll.cassette.surfaces.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.ui.BikeUiState
import com.r0adkll.cassette.bikes.ui.toLoaded
import com.r0adkll.cassette.capture.api.RefreshProgress
import com.r0adkll.cassette.capture.api.SharedRefresh
import com.r0adkll.cassette.common.compose.components.word
import com.r0adkll.cassette.common.compose.theme.StatusTone
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.Tip
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the tile needs from the app graph. */
@ContributesTo(AppScope::class)
interface TileComponent {
  val bikeRepository: BikeRepository
  val clock: Clock
  val tileRefresh: TileRefresh
  val settings: CassetteSettings
}

/** Implemented by the Application so the tile service, which Android creates, reaches the graph. */
interface TileHost {
  val tileComponent: TileComponent

  /** Outlives the tile service: a refresh keeps going after the shade closes. */
  val tileScope: CoroutineScope
}

/**
 * Starts the shared 60-second refresh from the tile. It runs in the app's scope, not the service's,
 * so it finishes even after the shade closes; the tile shows its progress while it's visible, and
 * also when the watch asked for it.
 */
@SingleIn(AppScope::class)
@Inject
class TileRefresh(private val refresh: SharedRefresh) {
  val progress: StateFlow<RefreshProgress?>
    get() = refresh.progress

  private var job: Job? = null

  fun start(scope: CoroutineScope, context: Context) {
    if (job?.isActive == true) return
    job = scope.launch {
      val updates = launch { refresh.progress.collect { requestListening(context) } }
      refresh.run()
      updates.cancel()
      requestListening(context)
    }
  }

  private fun requestListening(context: Context) =
    TileService.requestListeningState(context, ComponentName(context, BikeTileService::class.java))
}

/**
 * Quick Settings tile (decision 0012): the default bike's weakest status in the subtitle, with a
 * status icon; Android draws the tile, so our colours don't apply. Tapping runs the 60-second
 * refresh.
 */
class BikeTileService : TileService() {
  private var listening: CoroutineScope? = null

  private val host
    get() = applicationContext as TileHost

  /** Added from the shade's editor or the bike screen's tip: either way the tip is done. */
  override fun onTileAdded() {
    super.onTileAdded()
    host.tileScope.launch { host.tileComponent.settings.dismissTip(Tip.QuickSettingsTile) }
  }

  override fun onStartListening() {
    super.onStartListening()
    val graph = host.tileComponent
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    listening = scope
    scope.launch {
      combine(
          graph.bikeRepository.observeOverview(null).map { it?.toLoaded(graph.clock.now()) },
          graph.tileRefresh.progress,
        ) { bike, refresh ->
          bike to refresh
        }
        .collect { (bike, refresh) -> render(bike, refresh) }
    }
  }

  override fun onStopListening() {
    listening?.cancel()
    listening = null
    super.onStopListening()
  }

  override fun onClick() {
    super.onClick()
    host.tileComponent.tileRefresh.start(host.tileScope, applicationContext)
  }

  private fun render(bike: BikeUiState.Loaded?, refresh: RefreshProgress?) {
    val tile = qsTile ?: return
    val view = tileView(bike, refresh)
    tile.label = view.label
    tile.subtitle = view.subtitle
    tile.stateDescription = view.description
    tile.state = if (view.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    tile.icon = Icon.createWithResource(this, view.icon)
    tile.updateTile()
  }
}

/** The tile's content, separated from Android for testing. */
internal data class TileView(
  val label: String,
  val subtitle: String,
  val description: String,
  val active: Boolean,
  val icon: Int,
)

internal fun tileView(bike: BikeUiState.Loaded?, refresh: RefreshProgress?): TileView {
  if (refresh is RefreshProgress.Scanning) {
    val seconds = refresh.secondsLeft.toString().padStart(2, '0')
    return TileView(
      label = bike?.name ?: "Cassette",
      subtitle = "Wake the bike · 0:$seconds",
      description = "Scanning for the bike",
      active = true,
      icon = R.drawable.lucide_refresh_cw,
    )
  }
  if (refresh is RefreshProgress.NotReady) {
    return TileView(
      bike?.name ?: "Cassette",
      "Turn on Bluetooth",
      "Bluetooth is off or not allowed",
      false,
      R.drawable.lucide_circle_help,
    )
  }
  if (bike == null) {
    return TileView("Cassette", "No bike yet", "No bike added", false, R.drawable.lucide_bike)
  }
  val tone = bike.status.tone
  val seen = bike.status.meta.removePrefix("Seen ").removeSuffix(" ago")
  val weakest = bike.components.filter { it.value != "—" }.minByOrNull { it.level }
  val subtitle =
    when (tone) {
      StatusTone.Good -> "All good · $seen"
      StatusTone.Low,
      StatusTone.Critical -> {
        val worst = bike.components.firstOrNull { it.tone == tone } ?: weakest
        "${worst?.code} ${worst?.value} · $seen"
      }
      StatusTone.Stale -> bike.status.meta
      StatusTone.Unknown -> bike.status.headline.removeSuffix(".")
    }
  return TileView(
    label = bike.name,
    subtitle = subtitle,
    description = "${bike.name}: ${tone.word.lowercase()}. ${bike.status.summary}",
    active = tone != StatusTone.Stale && tone != StatusTone.Unknown,
    icon =
      when (tone) {
        StatusTone.Good -> R.drawable.lucide_circle_check
        StatusTone.Low -> R.drawable.lucide_battery_low
        StatusTone.Critical -> R.drawable.lucide_battery_warning
        StatusTone.Stale -> R.drawable.lucide_history
        StatusTone.Unknown -> R.drawable.lucide_circle_help
      },
  )
}
