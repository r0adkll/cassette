package com.r0adkll.cassette.common.compose.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A ring: when it left the circle (frame time, ms), its colour, and how long it flies. */
internal class PulseRing(val start: Long, val color: Color, val millis: Int)

/**
 * Rings that leave a circle while something listens (the tabletop refresh, waking a bike to add
 * it): each leaves at the circle's edge, 6 dp thick, then thins to 1 dp and fades as it grows. Draw
 * them with [Modifier.pulses]; [beat] lets the circle itself swell as each one leaves.
 */
@Stable
class Pulses internal constructor() {
  internal val rings = mutableStateListOf<PulseRing>()
  internal var now by mutableLongStateOf(0L)

  /** 0 to 1: a quick swell as a ring leaves, settled by the time it's a third of the way out. */
  val beat: Float
    get() {
      val age = now - (rings.maxOfOrNull { it.start } ?: return 0f)
      return when {
        age < 0 -> 0f
        age < BeatRiseMillis -> age.toFloat() / BeatRiseMillis
        age < BeatRiseMillis + BeatFallMillis ->
          1f - (age - BeatRiseMillis).toFloat() / BeatFallMillis
        else -> 0f
      }
    }
}

/**
 * Emits [Pulses]: while [emitting], one ring of [color] every 0.9 s, the first after [delayMillis]
 * (the tabletop waits for its circle to turn into the ring). Stopping lets the rings in flight
 * finish rather than vanish. [once] sends a single quicker ring of that colour, [onceDelayMillis]
 * after it's set (the tabletop's Done). Timed off the frame clock, so a slow frame doesn't bunch
 * them and renders can step through it.
 */
@Composable
fun rememberPulses(
  emitting: Boolean,
  color: Color,
  delayMillis: Int = 0,
  once: Color? = null,
  onceDelayMillis: Int = 200,
): Pulses {
  val pulses = remember { Pulses() }
  val rings = pulses.rings
  LaunchedEffect(emitting, color, once) {
    var started = -1L
    var emitted = 0L
    var onceSent = once == null
    while (emitting || !onceSent || rings.isNotEmpty()) {
      withFrameMillis { t ->
        if (started < 0) started = t
        if (emitting) {
          // Every ring due by now, each at its own start.
          val due = (t - started - delayMillis).let { if (it < 0) 0 else it / EveryMillis + 1 }
          while (emitted < due) {
            rings += PulseRing(started + delayMillis + emitted * EveryMillis, color, LifeMillis)
            emitted++
          }
        }
        if (!onceSent && once != null && t - started >= onceDelayMillis) {
          rings += PulseRing(t, once, LifeMillis * 2 / 5)
          onceSent = true
        }
        rings.removeAll { t - it.start >= it.millis }
        pulses.now = t
      }
    }
  }
  return pulses
}

/**
 * Draws [pulses] behind this, growing from radius [from] (the circle's edge) to [to], around
 * [centre] (this element's centre unless given).
 */
fun Modifier.pulses(
  pulses: Pulses,
  from: Dp,
  to: Dp,
  centre: DrawScope.() -> Offset = { center },
): Modifier = drawBehind {
  val at = centre()
  pulses.rings.forEach { ring ->
    val t = Spread.transform(((pulses.now - ring.start).toFloat() / ring.millis).coerceIn(0f, 1f))
    val width = mix(6.dp.toPx(), 1.dp.toPx(), t)
    drawCircle(
      ring.color.copy(alpha = mix(0.85f, 0f, t)),
      radius = mix(from.toPx(), to.toPx(), t) - width / 2,
      center = at,
      style = Stroke(width),
    )
  }
}

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

private val Spread = CubicBezierEasing(0.2f, 0f, 0.4f, 1f)
private const val LifeMillis = 3600
private const val EveryMillis = 900L
private const val BeatRiseMillis = 120
private const val BeatFallMillis = 360
