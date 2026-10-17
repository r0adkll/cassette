package com.r0adkll.cassette.app.surfaces

import android.app.Application
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import com.r0adkll.cassette.bikes.api.HomeSurfaces
import com.r0adkll.cassette.surfaces.tile.BikeTileService
import com.r0adkll.cassette.surfaces.widget.CassetteWidgets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Pins the bike widget through the launcher and adds the tile through the system (Android 13+).
 * Both show their own dialog; the result isn't needed, since the tip is done either way.
 */
@ContributesBinding(AppScope::class)
@Inject
class AndroidHomeSurfaces(private val application: Application) : HomeSurfaces {
  private val widgets
    get() = AppWidgetManager.getInstance(application)

  override fun canOfferWidget(): Boolean =
    widgets.isRequestPinAppWidgetSupported &&
      CassetteWidgets.all(application).all { widgets.getAppWidgetIds(it).isEmpty() }

  override fun requestWidget() {
    widgets.requestPinAppWidget(CassetteWidgets.bike(application), null, null)
  }

  override fun canOfferTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

  override fun requestTile() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    application.getSystemService(StatusBarManager::class.java).requestAddTileService(
      ComponentName(application, BikeTileService::class.java),
      application.getString(com.r0adkll.cassette.surfaces.tile.R.string.tile_label),
      Icon.createWithResource(
        application,
        com.r0adkll.cassette.surfaces.tile.R.drawable.lucide_bike,
      ),
      application.mainExecutor,
    ) {}
  }
}
