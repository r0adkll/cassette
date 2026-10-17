package com.r0adkll.cassette.data.ble

import kotlinx.coroutines.CoroutineScope

/**
 * Implemented by the Application so receivers, which Android creates, reach the graph (decision
 * 0007).
 */
interface BleHost {
  val bleScope: CoroutineScope
  val backgroundDeliveryHandler: BackgroundDeliveryHandler

  /** Re-arms the background scan if it should be on (after a reboot or an app update). */
  suspend fun rearmBackgroundCapture(reason: String)
}
