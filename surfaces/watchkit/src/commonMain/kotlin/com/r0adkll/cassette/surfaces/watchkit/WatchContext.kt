package com.r0adkll.cassette.surfaces.watchkit

import com.r0adkll.cassette.surfaces.wear.sync.WearCodec
import com.r0adkll.cassette.surfaces.wear.sync.WearRefresh
import com.r0adkll.cassette.surfaces.widgetkit.WidgetCodec
import com.r0adkll.cassette.surfaces.widgetkit.WidgetSnapshot

/**
 * The WatchConnectivity contract with the Apple Watch app (decision 0017), read by
 * `Watch/PhoneLink.swift`. The phone sends one application context, which WatchConnectivity keeps
 * only the latest of and hands the watch when it next runs: the widgets' snapshot, already worded,
 * and the refresh's state in Wear's shape.
 */
object WatchContext {
  /** [WidgetSnapshot] as JSON. */
  const val SNAPSHOT = "snapshot"

  /** [WearRefresh] as JSON. */
  const val REFRESH = "refresh"

  /** A message from the watch: `[REQUEST: REQUEST_REFRESH]`, answered with `[REPLY: …]`. */
  const val REQUEST = "request"
  const val REQUEST_REFRESH = "refresh"
  const val REPLY = "reply"
  const val REPLY_STARTED = "started"

  fun build(snapshot: WidgetSnapshot?, refresh: WearRefresh?): Map<String, String> = buildMap {
    snapshot?.let { put(SNAPSHOT, WidgetCodec.encode(it)) }
    refresh?.let { put(REFRESH, WearCodec.encode(it).decodeToString()) }
  }

  /**
   * What the complications show: each bike's status and weakest value. iOS allows about 50
   * complication pushes a day, so they go only when this changes (or after a quiet spell).
   */
  fun complicationKey(snapshot: WidgetSnapshot): List<String> =
    snapshot.bikes.map { bike ->
      val weakest = bike.current.components.firstOrNull { it.id == bike.current.weakestId }
      "${bike.id}:${bike.current.tone}:${weakest?.value}"
    } + "${snapshot.defaultBikeId}"
}
