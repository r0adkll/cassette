package com.r0adkll.cassette.bikes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.r0adkll.cassette.bikes.summary.ComponentRowModel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteRed
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone

/**
 * The bike screen on a half-open foldable in tabletop (decision 0014, iteration 10d): split at the
 * fold, readings above and the refresh below. [hinge] is the fold in window pixels.
 */
@Composable
internal fun TabletopBike(state: BikeUiState.Loaded, hinge: Rect, modifier: Modifier = Modifier) {
  var bounds by remember { mutableStateOf<Rect?>(null) }
  val density = LocalDensity.current
  Box(modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInWindow() }) {
    val window = bounds ?: return@Box
    // The fold in this screen's own coordinates, kept inside it.
    val top = (hinge.top - window.top).coerceIn(0f, window.height)
    val bottom = (hinge.bottom - window.top).coerceIn(top, window.height)
    with(density) { BikeTabletop(state, foldTop = top.toDp(), foldBottom = bottom.toDp()) }
  }
}

/** [TabletopBike] with the fold placed; renders call it directly. */
@Composable
internal fun BikeTabletop(state: BikeUiState.Loaded, foldTop: Dp, foldBottom: Dp) {
  val colors = CassetteTheme.colors
  Column(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().height(foldTop)) {
      HalfStatus(state)
      Row(
        Modifier.weight(1f)
          .fillMaxWidth()
          .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        state.components.forEach { row ->
          LevelColumn(
            row,
            waiting = state.heard != null && row.id !in state.heard,
            answered = state.heard?.contains(row.id) == true,
            lastRead = state.lastRead,
            ended = state.refresh is RefreshButton.Done,
            onClick = { state.eventSink(BikeEvent.OpenComponent(row.id)) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
        }
      }
    }
    Spacer(Modifier.height(foldBottom - foldTop))
    RefreshHalf(state, Modifier.weight(1f).fillMaxWidth().background(colors.panel))
  }
}

/** The top half's status: the bike's own block, or a live one while the scan runs. */
@Composable
private fun HalfStatus(state: BikeUiState.Loaded) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val scanning = state.refresh is RefreshButton.Scanning
  val block = colors.block(state.status.tone)
  val content = if (scanning) colors.text else block.content
  val heard = state.heard.orEmpty()
  val waiting = state.components.filter { it.id !in heard }
  Row(
    Modifier.fillMaxWidth()
      .then(
        if (scanning) Modifier.background(colors.panel).bottomLine(colors.line)
        else Modifier.background(block.container)
      )
      .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 16.dp),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (scanning) BlinkingDot(CassetteRed)
        Text(
          if (scanning) "${state.name} · Refreshing".uppercase()
          else "${state.name} · ${state.status.meta}".uppercase(),
          style = type.label,
          color = if (scanning) colors.muted else content.copy(alpha = 0.85f),
          maxLines = 1,
        )
      }
      Text(
        when {
          !scanning -> state.status.headline
          heard.isEmpty() -> "Listening."
          else -> "${heard.size} of ${state.components.size}."
        },
        style = type.display.copy(fontSize = 72.sp),
        color = content,
        maxLines = 1,
      )
    }
    Text(
      when {
        !scanning -> state.status.summary
        heard.isEmpty() -> "Shift once, or press the button on the rear derailleur."
        else -> waitingLine(waiting)
      },
      style = type.body.copy(lineHeight = 1.3.em),
      color = if (scanning) colors.muted else content,
      textAlign = TextAlign.End,
      modifier = Modifier.widthIn(max = 280.dp).padding(bottom = 4.dp),
    )
  }
}

/** "Waiting for the right shifter and the power meter. Spin the crank to wake the meter." */
private fun waitingLine(waiting: List<ComponentRowModel>): String {
  val names = waiting.map { "the ${it.name.lowercase()}" }
  val list =
    if (names.size <= 1) names.joinToString()
    else names.dropLast(1).joinToString(", ") + " and " + names.last()
  val meter = if (waiting.any { it.code == "PM" }) " Spin the crank to wake the meter." else ""
  return "Waiting for $list.$meter"
}

/**
 * A component as a tall column filled to its level. Waiting for the scan, it's grey; once it
 * answers, the fill rises in its status colour.
 */
@Composable
private fun LevelColumn(
  row: ComponentRowModel,
  waiting: Boolean,
  answered: Boolean,
  lastRead: String?,
  /** The scan is over, so a component still waiting didn't answer. */
  ended: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val rise = remember { Animatable(1f) }
  var wasWaiting by remember { mutableStateOf(waiting) }
  LaunchedEffect(waiting) {
    if (waiting) wasWaiting = true
    else if (wasWaiting) {
      wasWaiting = false
      rise.snapTo(0f)
      rise.animateTo(1f, tween(1080, easing = Settle))
    }
  }
  val fill = if (waiting) colors.line else colors.status(row.tone).copy(alpha = 0.32f)
  Box(
    modifier
      .clip(RoundedCornerShape(20.dp))
      .background(colors.track)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = "${row.name}, ${row.value}" }
  ) {
    Box(
      Modifier.align(Alignment.BottomCenter)
        .fillMaxWidth()
        .fillMaxHeight(row.level.coerceIn(0f, 1f))
        .graphicsLayer {
          scaleY = rise.value
          transformOrigin = TransformOrigin(0.5f, 1f)
        }
        .background(fill)
    )
    Column(
      Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        row.code,
        style = type.numeral.copy(fontSize = 18.sp, letterSpacing = 0.06.em),
        color = colors.text,
      )
      Column(
        Modifier.graphicsLayer { alpha = if (waiting) 0.45f else 1f },
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          row.value,
          style = type.numeral.copy(fontSize = 36.sp, lineHeight = 0.9.em),
          color = colors.text,
          maxLines = 1,
        )
        Text(
          when {
            waiting && ended -> "NO ANSWER"
            waiting -> "WAITING"
            answered -> "JUST NOW"
            else -> lastRead?.uppercase() ?: "NO READING"
          },
          style = type.status,
          color = colors.text,
          maxLines = 1,
        )
      }
    }
  }
}

/**
 * The bottom half: where the refresh stands on the left, and the circle that holds its one action
 * on the right. Pulse rings run under both and stop at the fold.
 */
@Composable
private fun RefreshHalf(state: BikeUiState.Loaded, modifier: Modifier) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val sink = state.eventSink
  val refresh = state.refresh
  val heard = state.heard.orEmpty()
  val total = state.components.size
  val allAnswered = total > 0 && heard.size == total
  Box(modifier.topLine(colors.line).clipToBounds()) {
    when (refresh) {
      is RefreshButton.Scanning -> PulseRings(CassetteRed, repeat = true)
      is RefreshButton.Done ->
        if (heard.isNotEmpty()) PulseRings(doneColor(allAnswered), repeat = false)
      else -> Unit
    }
    Row(
      Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val words = refreshWords(state, refresh, heard.size, total)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          when (refresh) {
            is RefreshButton.Scanning -> BlinkingDot(CassetteRed)
            is RefreshButton.Done -> Dot(doneColor(allAnswered))
            else -> Dot(colors.muted)
          }
          Text(words.state, style = type.label, color = colors.muted)
        }
        Text(
          words.big,
          style = type.numeralLarge.copy(fontSize = 96.sp),
          color = colors.text,
          maxLines = 1,
        )
        Text(
          words.body,
          style = type.body,
          color = colors.muted,
          modifier = Modifier.widthIn(max = 300.dp),
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          when (refresh) {
            is RefreshButton.Scanning -> {
              PillButton("Stop", primary = false, onClick = { sink(BikeEvent.StopRefresh) })
              PillButton("Add 60 s", primary = true, onClick = { sink(BikeEvent.MoreTime) })
            }
            is RefreshButton.Done ->
              PillButton("Scan again", primary = false, onClick = { sink(BikeEvent.Refresh) })
            else -> Unit
          }
        }
      }
      Box(Modifier.size(300.dp, 220.dp), contentAlignment = Alignment.Center) {
        when (refresh) {
          RefreshButton.Idle ->
            ActionCircle(
              LucideIcons.RefreshCw,
              "Refresh",
              "60 s scan",
              container = colors.text,
              content = colors.background,
              description = "Refresh now, 60 second scan",
              onClick = { sink(BikeEvent.Refresh) },
              breathe = true,
            )
          RefreshButton.Unavailable ->
            ActionCircle(
              LucideIcons.Bluetooth,
              "Turn on",
              "Bluetooth",
              container = colors.text,
              content = colors.background,
              description = "Turn on Bluetooth",
              onClick = { sink(BikeEvent.Refresh) },
              breathe = false,
            )
          is RefreshButton.Scanning -> ScanRing(refresh.secondsLeft, refresh.totalSeconds)
          is RefreshButton.Done -> {
            val block =
              colors.block(
                when {
                  allAnswered -> StatusTone.Good
                  heard.isNotEmpty() -> StatusTone.Low
                  else -> StatusTone.Unknown
                }
              )
            LandingCircle {
              ActionCircle(
                if (heard.isNotEmpty()) LucideIcons.Check else LucideIcons.BluetoothSearching,
                "Done",
                "${heard.size} of $total read",
                container = block.container,
                content = block.content,
                outline = block.outline,
                description = "Done, ${heard.size} of $total read",
                onClick = { sink(BikeEvent.FinishRefresh) },
                breathe = false,
              )
            }
          }
        }
      }
    }
  }
}

private class RefreshWords(val state: String, val big: String, val body: String)

private fun refreshWords(
  state: BikeUiState.Loaded,
  refresh: RefreshButton,
  heard: Int,
  total: Int,
): RefreshWords =
  when (refresh) {
    RefreshButton.Idle ->
      RefreshWords(
        "LAST READ",
        state.lastRead ?: "Never",
        if (state.lastRead != null)
          "Readings from your last ride. Tap the circle, then shift once to wake the bike."
        else "Tap the circle, then shift once to wake the bike.",
      )
    RefreshButton.Unavailable ->
      RefreshWords(
        "CAN'T SCAN",
        "Off",
        "Turn on Bluetooth and allow Nearby devices, then tap the circle.",
      )
    is RefreshButton.Scanning ->
      if (heard == 0)
        RefreshWords(
          "LISTENING",
          clock(refresh.secondsLeft),
          "Keep the phone close to the bike. Each component fills in above as it answers.",
        )
      else
        RefreshWords(
          "READING",
          clock(refresh.secondsLeft),
          "Components answer in any order. The scan stops early once all $total have.",
        )
    is RefreshButton.Done ->
      when {
        total > 0 && heard == total ->
          RefreshWords(
            "READ IN",
            "${refresh.seconds} s",
            "All $total components answered. Tap the circle to finish, or fold the phone.",
          )
        heard > 0 ->
          RefreshWords(
            "SCAN ENDED",
            "$heard of $total",
            "The rest didn't answer in time. Wake the bike and scan again, or tap the circle.",
          )
        else ->
          RefreshWords(
            "SCAN ENDED",
            "None",
            "Nothing answered. Shift once to wake the bike, then scan again.",
          )
      }
  }

/** "0:52", "2:05". */
private fun clock(seconds: Int): String =
  "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
private fun doneColor(allAnswered: Boolean): Color =
  if (allAnswered) CassetteTheme.colors.block(StatusTone.Good).container
  else CassetteTheme.colors.block(StatusTone.Low).container

/** The circle as a button: an icon and two lines. Ready, it breathes slowly. */
@Composable
private fun ActionCircle(
  icon: ImageVector,
  title: String,
  sub: String,
  container: Color,
  content: Color,
  description: String,
  onClick: () -> Unit,
  breathe: Boolean,
  outline: Color? = null,
) {
  val scale =
    if (breathe) {
      val breathing = rememberInfiniteTransition()
      breathing
        .animateFloat(
          1f,
          1.035f,
          infiniteRepeatable(tween(1800, easing = Breathe), RepeatMode.Reverse),
        )
        .value
    } else 1f
  Column(
    Modifier.scale(scale)
      .size(204.dp)
      .clip(CircleShape)
      .background(container)
      .then(if (outline != null) Modifier.border(1.dp, outline, CircleShape) else Modifier)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = description },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
  ) {
    Icon(icon, null, tint = content, modifier = Modifier.size(44.dp))
    Text(
      title.uppercase(),
      style = CassetteTheme.type.numeral.copy(letterSpacing = 0.1.em),
      color = content,
    )
    Text(
      sub.uppercase(),
      style = CassetteTheme.type.label.copy(fontSize = 12.sp, letterSpacing = 0.16.em),
      color = content.copy(alpha = 0.72f),
    )
  }
}

/** Done lands: in from 70%, a small overshoot, then still. */
@Composable
private fun LandingCircle(content: @Composable () -> Unit) {
  val land = remember { Animatable(0.7f) }
  LaunchedEffect(Unit) {
    land.animateTo(
      1f,
      keyframes {
        durationMillis = 800
        1.06f at 430 using Settle
      },
    )
  }
  Box(Modifier.scale(land.value)) { content() }
}

/** The scan's countdown: a red ring draining over the scan, around a beating dot. */
@Composable
private fun ScanRing(secondsLeft: Int, totalSeconds: Int) {
  val colors = CassetteTheme.colors
  val beat = rememberInfiniteTransition()
  val dot by
    beat.animateFloat(
      1f,
      1f,
      infiniteRepeatable(
        keyframes {
          durationMillis = 900
          1.18f at 135
          1f at 315
        }
      ),
    )
  val left = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds else 0f
  Canvas(Modifier.size(220.dp)) {
    val stroke = 6.dp.toPx()
    val radius = 96.dp.toPx()
    drawCircle(colors.panel, radius)
    drawCircle(colors.line, radius, style = Stroke(stroke))
    drawArc(
      CassetteRed,
      startAngle = -90f,
      sweepAngle = 360f * left,
      useCenter = false,
      topLeft = Offset(center.x - radius, center.y - radius),
      size = Size(radius * 2, radius * 2),
      style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawCircle(CassetteRed, 22.dp.toPx() * dot)
  }
}

/**
 * Rings that leave the circle at the timer ring's thickness, thin and fade as they grow to about
 * 3.8 times its size. While scanning one leaves every 0.9 s; Done sends one. The half clips them.
 */
@Composable
private fun PulseRings(color: Color, repeat: Boolean) {
  val phase =
    if (repeat) {
      rememberInfiniteTransition()
        .animateFloat(0f, 1f, infiniteRepeatable(tween(PulseMillis, easing = LinearEasing)))
        .value
    } else {
      val once = remember { Animatable(0f) }
      LaunchedEffect(Unit) { once.animateTo(1f, tween(PulseMillis * 2 / 5, easing = LinearEasing)) }
      // One ring over the first 40% of a pulse's length, as in the mockup.
      once.value
    }
  Box(
    Modifier.fillMaxSize().drawBehind {
      // The circle's centre: the right-hand 300 dp column, inside the half's padding.
      val centre = Offset(size.width - 28.dp.toPx() - 150.dp.toPx(), size.height / 2)
      val offsets = if (repeat) listOf(0f, 0.25f, 0.5f, 0.75f) else listOf(0f)
      offsets.forEach { offset ->
        val t = Pulse.transform(if (repeat) (phase - offset + 1f) % 1f else phase)
        if (!repeat && phase >= 1f) return@forEach
        val diameter = mix(200.dp.toPx(), 760.dp.toPx(), t)
        val width = mix(6.dp.toPx(), 1.dp.toPx(), t)
        drawCircle(
          color.copy(alpha = mix(0.85f, 0f, t)),
          radius = (diameter - width) / 2,
          center = centre,
          style = Stroke(width),
        )
      }
    }
  )
}

@Composable
private fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
  val colors = CassetteTheme.colors
  Box(
    Modifier.height(56.dp)
      .clip(CircleShape)
      .then(
        if (primary) Modifier.background(colors.text)
        else Modifier.border(1.dp, colors.line, CircleShape)
      )
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 26.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text.uppercase(),
      style = CassetteTheme.type.button.copy(letterSpacing = 0.12.em),
      color = if (primary) colors.background else colors.text,
    )
  }
}

@Composable
private fun Dot(color: Color) {
  Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun BlinkingDot(color: Color) {
  val blink by
    rememberInfiniteTransition()
      .animateFloat(1f, 0.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
  Box(Modifier.size(8.dp).graphicsLayer { alpha = blink }.clip(CircleShape).background(color))
}

private fun Modifier.topLine(color: Color): Modifier = drawBehind {
  drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

private fun Modifier.bottomLine(color: Color): Modifier = drawBehind {
  drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}

/** Decelerating, as the sheet and the mockup's landings. */
private val Settle = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Breathe = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val Pulse = CubicBezierEasing(0.2f, 0f, 0.4f, 1f)
private const val PulseMillis = 3600

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t
