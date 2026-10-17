package com.r0adkll.cassette.wear.data

import android.app.Application
import android.net.Uri
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.r0adkll.cassette.surfaces.wear.sync.WearCodec
import com.r0adkll.cassette.surfaces.wear.sync.WearPaths
import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import com.r0adkll.cassette.surfaces.wear.sync.WearSnapshot
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** The phone's data as the watch has it (decision 0013). The watch never scans. */
interface PhoneData {
  /** The phone's latest snapshot; null until it has sent one. */
  val snapshot: Flow<WearSnapshot?>

  /** The phone's refresh; null until one has run. */
  val refresh: Flow<WearRefresh?>

  /**
   * Asks the phone to run its 60-second refresh; false when no phone with Cassette is reachable.
   */
  suspend fun requestRefresh(): Boolean
}

/**
 * Reads the DataItems the phone publishes. The Data Layer keeps them on the watch, so the last
 * snapshot is there while the phone is away.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DataLayerPhoneData(private val application: Application) : PhoneData {
  private val dataClient by lazy { Wearable.getDataClient(application) }

  override val snapshot: Flow<WearSnapshot?> =
    item(WearPaths.SNAPSHOT).map { bytes -> bytes?.let(WearCodec::decodeSnapshot) }

  override val refresh: Flow<WearRefresh?> =
    item(WearPaths.REFRESH).map { bytes -> bytes?.let(WearCodec::decodeRefresh) }

  override suspend fun requestRefresh(): Boolean =
    try {
      val phones =
        Wearable.getCapabilityClient(application)
          .getCapability(WearPaths.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
          .await()
          .nodes
      val phone = phones.firstOrNull { it.isNearby } ?: phones.firstOrNull()
      if (phone == null) false
      else {
        Wearable.getMessageClient(application)
          .sendMessage(phone.id, WearPaths.REQUEST_REFRESH, null)
          .await()
        true
      }
    } catch (e: ApiException) {
      false
    }

  /**
   * The bytes of the DataItem at [path], from whichever node wrote it, and again on every change. A
   * phone's item wins over a local one (the debug build's sample data).
   */
  private fun item(path: String): Flow<ByteArray?> = callbackFlow {
    val listener = DataClient.OnDataChangedListener { events ->
      events
        .filter { it.dataItem.uri.path == path }
        .lastOrNull()
        ?.let { event ->
          trySend(if (event.type == DataEvent.TYPE_CHANGED) event.dataItem.data else null)
        }
    }
    val any = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority("*").path(path)
    dataClient.addListener(listener, any.build(), DataClient.FILTER_LITERAL).await()

    val local = Wearable.getNodeClient(application).localNode.await().id
    val items = dataClient.getDataItems(Uri.Builder().scheme("wear").path(path).build()).await()
    val stored =
      try {
        items.map { it.uri.host to it.data }.sortedBy { (node, _) -> node == local }
      } finally {
        items.release()
      }
    send(stored.firstOrNull()?.second)

    awaitClose { dataClient.removeListener(listener) }
  }
    .distinctUntilChanged { old, new -> old.contentEquals(new) }
}
