package com.r0adkll.cassette.wear.data

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.r0adkll.cassette.wear.complication.BikeComplicationService
import com.r0adkll.cassette.wear.tile.BikeTileService

/**
 * The phone sent a snapshot or refresh state: re-render the tile (and complications), which read it
 * on demand. The app's screens listen for themselves while open.
 */
class PhoneDataListenerService : WearableListenerService() {
  override fun onDataChanged(events: DataEventBuffer) {
    TileService.getUpdater(this).requestUpdate(BikeTileService::class.java)
    BikeComplicationService.update(this)
  }
}
