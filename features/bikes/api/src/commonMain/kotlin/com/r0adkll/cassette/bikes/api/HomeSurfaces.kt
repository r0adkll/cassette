package com.r0adkll.cassette.bikes.api

/**
 * The widget and Quick Settings tile, as the bike screen's tips offer them (decision 0011). The
 * platform provides it; asking is fire-and-forget, since the system shows its own dialog.
 */
interface HomeSurfaces {
  /** The launcher can place a widget from the app, and no Cassette widget is placed yet. */
  fun canOfferWidget(): Boolean

  fun requestWidget()

  /** The system can add the tile from the app (Android 13+). */
  fun canOfferTile(): Boolean

  fun requestTile()
}
