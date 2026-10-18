package com.r0adkll.cassette.bikes.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.r0adkll.cassette.common.compose.components.pulses
import com.r0adkll.cassette.common.compose.components.rememberPulses
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
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
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
    // Both halves run under the system bars (decision 0016) and pad their content inside them.
    RefreshHalf(
      state,
      Modifier.weight(1f)
        .fillMaxWidth()
        .background(colors.panel)
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        ),
    )
  }
}

/** The top half's status: the bike's own block, or a live one while the scan runs. */
@Composable
private fun HalfStatus(state: BikeUiState.Loaded) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val scanning = state.refresh is RefreshButton.Scanning
  val block = colors.block(state.status.tone)
  val heard = state.heard.orEmpty()
  val waiting = state.components.filter { it.id !in heard }
  // The block eases between the bike's status colour and the live panel.
  val morph = tween<Color>(MorphMillis, easing = Settle)
  val container by animateColorAsState(if (scanning) colors.panel else block.container, morph)
  val content by animateColorAsState(if (scanning) colors.text else block.content, morph)
  val quiet by animateColorAsState(if (scanning) colors.muted else block.content, morph)
  val line by animateColorAsState(if (scanning) colors.line else block.container, morph)
  Row(
    Modifier.fillMaxWidth()
      .background(container)
      .bottomLine(line)
      .windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
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
          color = if (scanning) quiet else quiet.copy(alpha = 0.85f),
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
      color = quiet,
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
  // Waiting greys out, and answering colours in, gradually; the rise above plays on top.
  val fill by
    animateColorAsState(
      if (waiting) colors.line else colors.status(row.tone).copy(alpha = 0.32f),
      tween(MorphMillis, easing = Settle),
    )
  val readout by animateFloatAsState(if (waiting) 0.45f else 1f, tween(MorphMillis))
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
        Modifier.graphicsLayer { alpha = readout },
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
  val doneRing = colors.block(doneTone(heard.size, total)).container
  // Rings leave the circle once it has turned into the ring, about 3.8 times its size at the end;
  // the half clips them, so none crosses the fold. Done sends one in its colour as it lands.
  val pulses =
    rememberPulses(
      emitting = refresh is RefreshButton.Scanning,
      color = CassetteRed,
      delayMillis = MorphMillis,
      once = doneRing.takeIf { refresh is RefreshButton.Done && heard.isNotEmpty() },
    )
  Box(
    modifier.topLine(colors.line).clipToBounds().pulses(pulses, from = 100.dp, to = 380.dp) {
      // The circle's centre: the right-hand 300 dp column, inside the half's padding.
      Offset(size.width - 28.dp.toPx() - 150.dp.toPx(), size.height / 2)
    }
  ) {
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
          val dot by
            animateColorAsState(
              when (refresh) {
                is RefreshButton.Scanning -> CassetteRed
                is RefreshButton.Done -> doneRing
                else -> colors.muted
              },
              tween(MorphMillis),
            )
          if (refresh is RefreshButton.Scanning) BlinkingDot(dot) else Dot(dot)
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
        RefreshCircle(
          refresh,
          heard.size,
          total,
          onRefresh = { sink(BikeEvent.Refresh) },
          onFinish = { sink(BikeEvent.FinishRefresh) },
        )
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

/** Done's colour: good when everything answered, low when some did, grey when none. */
private fun doneTone(heard: Int, total: Int): StatusTone =
  when {
    total > 0 && heard == total -> StatusTone.Good
    heard > 0 -> StatusTone.Low
    else -> StatusTone.Unknown
  }

/** What the circle says, which crossfades as the refresh moves on. */
private enum class CircleFace {
  Ready,
  Bluetooth,
  Scanning,
  Done,
}

/**
 * The bottom half's one action, a single circle that changes with the refresh rather than three
 * that swap. Ready, it's the Refresh button, its disc breathing. When the scan starts it eases into
 * the panel colour as the red countdown ring and its beating dot come in; the ring drains smoothly
 * between the once-a-second updates. Done fills it in the result's colour with a small landing.
 */
@Composable
private fun RefreshCircle(
  refresh: RefreshButton,
  heard: Int,
  total: Int,
  onRefresh: () -> Unit,
  onFinish: () -> Unit,
) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val face =
    when (refresh) {
      RefreshButton.Idle -> CircleFace.Ready
      RefreshButton.Unavailable -> CircleFace.Bluetooth
      is RefreshButton.Scanning -> CircleFace.Scanning
      is RefreshButton.Done -> CircleFace.Done
    }
  val scanning = face == CircleFace.Scanning
  val done = colors.block(doneTone(heard, total))
  val morph = tween<Color>(MorphMillis, easing = Settle)
  val fill by
    animateColorAsState(
      when (face) {
        CircleFace.Ready,
        CircleFace.Bluetooth -> colors.text
        CircleFace.Scanning -> colors.panel
        CircleFace.Done -> done.container
      },
      morph,
    )
  val content by
    animateColorAsState(
      if (face == CircleFace.Done) done.content else colors.background,
      morph,
    )
  val outline by
    animateColorAsState(
      if (face == CircleFace.Done) done.outline ?: Color.Transparent else Color.Transparent,
      morph,
    )
  val ring by animateFloatAsState(if (scanning) 1f else 0f, tween(MorphMillis, easing = Settle))
  // The dot arrives once the ring has.
  val dot by
    animateFloatAsState(
      if (scanning) 1f else 0f,
      tween(MorphMillis, delayMillis = if (scanning) MorphMillis / 2 else 0, easing = Settle),
    )
  val radius by
    animateFloatAsState(if (scanning) 96f else 102f, tween(MorphMillis, easing = Settle))
  // Progress arrives once a second; easing linearly over that second keeps the drain continuous.
  val left =
    if (refresh is RefreshButton.Scanning && refresh.totalSeconds > 0)
      refresh.secondsLeft.toFloat() / refresh.totalSeconds
    else 1f
  val sweep by animateFloatAsState(left, tween(1000, easing = LinearEasing))

  val motion = rememberInfiniteTransition()
  val breath by
    motion.animateFloat(
      0f,
      1f,
      infiniteRepeatable(tween(1800, easing = Breathe), RepeatMode.Reverse),
    )
  val beat by
    motion.animateFloat(
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
  // Breathing fades out rather than stopping mid-breath.
  val breathing by animateFloatAsState(if (face == CircleFace.Ready) 1f else 0f, tween(MorphMillis))

  // Done lands: a dip, a small overshoot, then still.
  val land = remember { Animatable(1f) }
  LaunchedEffect(face == CircleFace.Done) {
    if (face == CircleFace.Done)
      land.animateTo(
        1f,
        keyframes {
          durationMillis = 750
          0.9f at 150 using Settle
          1.06f at 450 using Settle
        },
      )
  }

  Box(
    Modifier.size(220.dp)
      .scale(land.value)
      .clip(CircleShape)
      .clickable(
        enabled = !scanning,
        role = Role.Button,
        onClick = if (face == CircleFace.Done) onFinish else onRefresh,
      )
      .semantics {
        contentDescription =
          when (face) {
            CircleFace.Ready -> "Refresh now, 60 second scan"
            CircleFace.Bluetooth -> "Turn on Bluetooth"
            CircleFace.Scanning -> "Scanning"
            CircleFace.Done -> "Done, $heard of $total read"
          }
      },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.fillMaxSize()) {
      // Breathing grows the disc alone; the icon and words stay still on it.
      val r = radius.dp.toPx() * (1f + 0.035f * breath * breathing)
      val stroke = 6.dp.toPx()
      drawCircle(fill, r)
      if (outline.alpha > 0f) drawCircle(outline, r, style = Stroke(1.dp.toPx()))
      if (ring > 0f) {
        drawCircle(colors.line.copy(alpha = ring), r, style = Stroke(stroke))
        drawArc(
          CassetteRed.copy(alpha = ring),
          startAngle = -90f,
          sweepAngle = 360f * sweep,
          useCenter = false,
          topLeft = Offset(center.x - r, center.y - r),
          size = Size(r * 2, r * 2),
          style = Stroke(stroke, cap = StrokeCap.Round),
        )
      }
      if (dot > 0f) drawCircle(CassetteRed, 22.dp.toPx() * dot * beat)
    }
    Crossfade(face, animationSpec = tween(MorphMillis)) { shown ->
      val (icon, title, sub) =
        when (shown) {
          CircleFace.Ready -> Triple(LucideIcons.RefreshCw, "Refresh", "60 s scan")
          CircleFace.Bluetooth -> Triple(LucideIcons.Bluetooth, "Turn on", "Bluetooth")
          CircleFace.Done ->
            Triple(
              if (heard > 0) LucideIcons.Check else LucideIcons.BluetoothSearching,
              "Done",
              "$heard of $total read",
            )
          // The ring and the dot say it.
          CircleFace.Scanning -> return@Crossfade
        }
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(44.dp))
        Text(title.uppercase(), style = type.numeral.copy(letterSpacing = 0.1.em), color = content)
        Text(
          sub.uppercase(),
          style = type.label.copy(fontSize = 12.sp, letterSpacing = 0.16.em),
          color = content.copy(alpha = 0.72f),
        )
      }
    }
  }
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
/** How long the circle takes to turn from one state to the next; the pulses wait for it. */
private const val MorphMillis = 450

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t
