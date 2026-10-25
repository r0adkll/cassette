package com.r0adkll.cassette.common.screens

import com.slack.circuit.runtime.screen.Screen

/**
 * Links into the app from notifications and widgets. They are only sent to the app's own activity,
 * never exported: `cassette://bike/{bikeId}` and
 * `cassette://bike/{bikeId}/component/{componentId}`. The one link a browser opens is Drop's Open
 * in App, `cassette://drop/{buildId}`, in the builds Drop installs (decision 0008); it opens no
 * screen.
 */
object DeepLinks {
  const val SCHEME = "cassette"

  fun bike(bikeId: Long): String = "$SCHEME://bike/$bikeId"

  fun component(bikeId: Long, componentId: Long): String = "${bike(bikeId)}/component/$componentId"

  /** The back stack a link opens, root first; null for a link this build doesn't know. */
  fun parse(link: String): List<Screen>? {
    val path = link.removePrefix("$SCHEME://").takeIf { it != link } ?: return null
    val parts = path.trimEnd('/').split('/')
    if (parts.getOrNull(0) != "bike") return null
    val bike = BikeScreen(parts.getOrNull(1)?.toLongOrNull() ?: return null)
    return when (parts.size) {
      2 -> listOf(bike)
      4 if parts[2] == "component" ->
        listOf(bike, ComponentScreen(parts[3].toLongOrNull() ?: return null))
      else -> null
    }
  }

  /** The Build ID in Drop's Open in App link; null for any other link. */
  fun dropBuild(link: String): String? {
    val buildId = link.removePrefix("$SCHEME://drop/").takeIf { it != link } ?: return null
    return buildId.trimEnd('/').takeIf { it.isNotEmpty() && '/' !in it }
  }
}
