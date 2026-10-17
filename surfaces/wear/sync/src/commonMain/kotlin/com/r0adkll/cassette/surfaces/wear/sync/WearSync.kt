package com.r0adkll.cassette.surfaces.wear.sync

/** Where the phone and the watch meet on the Wear Data Layer (decision 0013). */
object WearPaths {
  /** DataItem, phone to watch: the [WearSnapshot]. */
  const val SNAPSHOT = "/cassette/snapshot"

  /** DataItem, phone to watch: the phone's [WearRefresh]. */
  const val REFRESH = "/cassette/refresh"

  /** Message, watch to phone: run the 60-second refresh. */
  const val REQUEST_REFRESH = "/cassette/request-refresh"

  /** Declared by the phone app, so the watch can find it. */
  const val PHONE_CAPABILITY = "cassette_phone"

  /** Declared by the watch app, so the phone's Diagnostics can tell whether it's installed. */
  const val WATCH_CAPABILITY = "cassette_watch"
}

/** A watch the phone is connected to, for Diagnostics. */
data class ConnectedWatch(val name: String, val hasCassette: Boolean)

/** The phone's connected watches; null when the Wear Data Layer isn't available on this phone. */
fun interface ConnectedWatches {
  suspend fun list(): List<ConnectedWatch>?
}
