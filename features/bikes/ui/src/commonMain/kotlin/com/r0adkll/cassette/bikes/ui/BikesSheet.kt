package com.r0adkll.cassette.bikes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.r0adkll.cassette.common.compose.components.BarAction
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.LocalTopBarShown
import com.r0adkll.cassette.common.compose.components.StatusLabel
import com.r0adkll.cassette.common.compose.components.TopBarActions
import com.r0adkll.cassette.common.compose.components.TopBarNavigation
import com.r0adkll.cassette.common.compose.components.hasSystemActionBar
import com.r0adkll.cassette.common.compose.components.icon
import com.r0adkll.cassette.common.compose.components.word
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.isShortWindow
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.compose.theme.StatusTone
import kotlin.math.roundToInt

/** The other bikes that need charging, at the worst tone among them (decision 0026). */
internal class BikesAttention(val tone: StatusTone, val names: List<String>)

/** The bikes besides [current] that need charging now, else those that are low; null if none. */
internal fun List<BikeChoice>.attention(current: Long): BikesAttention? {
  val others = filter { it.id != current }
  val tone =
    listOf(StatusTone.Critical, StatusTone.Low).firstOrNull { tone ->
      others.any { it.tone == tone }
    } ?: return null
  return BikesAttention(tone, others.filter { it.tone == tone }.map { it.name })
}

/** What screen readers say for the bikes pill: "3 bikes, Gravel bike is low. Switch bike". */
internal fun bikesDescription(count: Int, attention: BikesAttention?): String = buildString {
  append("$count bikes")
  if (attention != null) {
    val names = attention.names
    append(", ")
    append(
      if (names.size > 1) names.dropLast(1).joinToString() + " and " + names.last() else names[0]
    )
    val one = names.size == 1
    append(
      when (attention.tone) {
        StatusTone.Critical -> if (one) " needs charging now" else " need charging now"
        else -> if (one) " is low" else " are low"
      }
    )
  }
  append(". Switch bike")
}

/** The sheet's kicker: "3 bikes · 1 low". */
internal fun bikesKicker(count: Int, attention: BikesAttention?): String =
  "$count bikes" + attention?.let { " · ${it.names.size} ${it.tone.word.lowercase()}" }.orEmpty()

/**
 * The phone's bikes (decision 0026), opened from the bikes pill: a sheet from the bottom with a row
 * per bike, the one on screen outlined, and a bike that needs charging saying why. Tapping a row
 * shows that bike. Close, the scrim, dragging it down by its top or back closes it. Its Close and
 * Add bike go in the iPhone Duo's vertical bar; on a phone on its side, too short for Add bike at
 * the foot, Add bike sits beside Close.
 */
@Composable
internal fun BoxScope.BikesSheet(state: BikeUiState.Loaded, open: Boolean, onClose: () -> Unit) {
  val colors = CassetteTheme.colors
  NavigationBackHandler(
    rememberNavigationEventState(NavigationEventInfo.None),
    isBackEnabled = open,
    onBackCompleted = onClose,
  )
  AnimatedVisibility(
    open,
    Modifier.matchParentSize(),
    enter = fadeIn(tween(SheetInMillis)),
    exit = fadeOut(tween(SheetOutMillis)),
  ) {
    Box(
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = if (colors.isDark) 0.6f else 0.32f))
        .clickable(
          interactionSource = null,
          indication = null,
          onClickLabel = "Close",
          onClick = onClose,
        )
    )
  }
  AnimatedVisibility(
    open,
    // Clear of the status bar and a little more, however many bikes there are.
    Modifier.align(Alignment.BottomCenter)
      .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
      .padding(top = 48.dp)
      .widthIn(max = SheetWidth)
      .fillMaxWidth(),
    enter = slideInVertically(tween(SheetInMillis, easing = EmphasizedDecelerate)) { it },
    exit = slideOutVertically(tween(SheetOutMillis, easing = EmphasizedAccelerate)) { it },
  ) {
    // Its buttons give the bar back as it starts to close (the Duo's vertical bar, iOS's glass).
    CompositionLocalProvider(LocalTopBarShown provides (LocalTopBarShown.current && open)) {
      SheetBody(state, onClose)
    }
  }
}

@Composable
private fun SheetBody(state: BikeUiState.Loaded, onClose: () -> Unit) {
  val colors = CassetteTheme.colors
  val sink = state.eventSink
  val bikes = state.choices()
  val attention = bikes.attention(state.bikeId)
  // Too short for Add bike at the foot; on the Duo it goes in the vertical bar.
  val addBikeOnTop = isShortWindow() || hasSystemActionBar()
  val addBike =
    BarAction(LucideIcons.BikePlus, "Add bike") {
      onClose()
      sink(BikeEvent.AddBike)
    }
  var drag by remember { mutableFloatStateOf(0f) }
  val dismissAt = with(LocalDensity.current) { DismissDrag.toPx() }
  Column(
    Modifier.offset { IntOffset(0, drag.roundToInt()) }
      .clip(SheetShape)
      .background(colors.panel)
      .windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
      )
      .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // The handle and the header drag it down; the rows scroll.
    Column(
      Modifier.draggable(
        rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
        Orientation.Vertical,
        onDragStopped = { velocity ->
          if (drag > dismissAt || velocity > DismissVelocity) onClose()
          else animate(drag, 0f) { value, _ -> drag = value }
        },
      ),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        Modifier.align(Alignment.CenterHorizontally)
          .padding(top = 10.dp)
          .size(36.dp, 4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(colors.line)
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Kicker(bikesKicker(bikes.size, attention))
          Text(
            "Your bikes",
            style =
              CassetteTheme.type.title.copy(fontSize = CassetteTheme.type.title.fontSize * 0.75f),
            color = colors.text,
          )
        }
        if (addBikeOnTop) TopBarActions(listOf(addBike))
        TopBarNavigation(LucideIcons.X, "Close", onClick = onClose)
      }
    }
    Column(
      Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      bikes.forEach { bike ->
        BikeRow(
          bike,
          selected = bike.id == state.bikeId,
          onClick = {
            onClose()
            sink(BikeEvent.SelectBike(bike.id))
          },
        )
      }
    }
    if (!addBikeOnTop)
      CassetteButton(
        "Add bike",
        onClick = addBike.onClick,
        primary = false,
        icon = LucideIcons.BikePlus,
      )
  }
}

/**
 * A bike in the sheet: its status icon, name, status word and when it was seen, and for a bike that
 * needs charging, why. The one on screen is outlined, with a check.
 */
@Composable
private fun BikeRow(bike: BikeChoice, selected: Boolean, onClick: () -> Unit) {
  val colors = CassetteTheme.colors
  val type = CassetteTheme.type
  val shape = RoundedCornerShape(16.dp)
  val needsCharge = bike.tone == StatusTone.Low || bike.tone == StatusTone.Critical
  Row(
    Modifier.fillMaxWidth()
      .clip(shape)
      .background(colors.background)
      .border(if (selected) 2.dp else 1.dp, if (selected) colors.text else colors.line, shape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { this.selected = selected }
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
      Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(colors.track),
      contentAlignment = Alignment.Center,
    ) {
      Icon(bike.tone.icon, null, tint = colors.status(bike.tone), modifier = Modifier.size(22.dp))
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        bike.name,
        style = type.numeral,
        color = colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        StatusLabel(bike.tone)
        Text(
          " · ${bike.seen.uppercase()}",
          style = type.status,
          color = colors.muted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (needsCharge && bike.summary.isNotEmpty())
        Text(bike.summary, style = type.bodySmall, color = colors.muted)
    }
    if (selected) Icon(LucideIcons.Check, null, tint = colors.text, modifier = Modifier.size(22.dp))
  }
}

/** The phone's width: a phone on its side gets the sheet centred at it. */
private val SheetWidth = 480.dp

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** How far, or how fast (px/s), a drag down closes the sheet; shorter springs back. */
private val DismissDrag = 96.dp
private const val DismissVelocity = 1200f
