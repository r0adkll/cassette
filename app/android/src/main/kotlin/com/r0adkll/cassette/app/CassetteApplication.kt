package com.r0adkll.cassette.app

import android.app.Application
import com.r0adkll.cassette.app.di.AndroidAppGraph
import com.r0adkll.cassette.data.ble.BackgroundDeliveryHandler
import com.r0adkll.cassette.data.ble.BleHost
import com.r0adkll.cassette.surfaces.tile.TileComponent
import com.r0adkll.cassette.surfaces.tile.TileHost
import com.r0adkll.cassette.surfaces.widget.WidgetComponent
import com.r0adkll.cassette.surfaces.widget.WidgetHost
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CassetteApplication : Application(), BleHost, WidgetHost, TileHost {
  /** Android entry points reach the graph through this (decision 0007). */
  val graph: AndroidAppGraph by lazy {
    createGraphFactory<AndroidAppGraph.Factory>().create(this)
  }

  /** Lives as long as the process: background wakes and widget updates run here. */
  override val bleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
    // Every process start, including background wakes, keeps widgets in step with the data.
    graph.widgetUpdater.start(bleScope)
  }

  override val backgroundDeliveryHandler: BackgroundDeliveryHandler
    get() = graph.backgroundDeliveryHandler

  override suspend fun rearmBackgroundCapture(reason: String) =
    graph.captureController.ensureBackgroundCapture(reason)

  override val widgetComponent: WidgetComponent
    get() = graph

  override val tileComponent: TileComponent
    get() = graph

  override val tileScope: CoroutineScope
    get() = bleScope
}
