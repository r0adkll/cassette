package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.RideSummary
import com.r0adkll.cassette.capture.api.RideVerdict
import com.r0adkll.cassette.common.compose.components.CassetteButton
import com.r0adkll.cassette.common.compose.components.CodeBadge
import com.r0adkll.cassette.common.compose.components.Kicker
import com.r0adkll.cassette.common.compose.components.Panel
import com.r0adkll.cassette.common.compose.icons.LucideIcons
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.common.screens.RideTestResultScreen
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock

data class RideTestResultUiState(
  /** Null if the test is gone (the log was cleared). */
  val test: RideSummary?,
  val names: Map<String, ComponentName>,
  val eventSink: (RideTestResultEvent) -> Unit,
) : CircuitUiState

sealed interface RideTestResultEvent : CircuitUiEvent {
  data object Done : RideTestResultEvent
}

/** How one ride test went: a verdict in words, the wakes across the ride, what was heard. */
@CircuitInject(RideTestResultScreen::class, AppScope::class)
@Inject
class RideTestResultPresenter(
  private val screen: RideTestResultScreen,
  private val navigator: Navigator,
  private val log: CaptureLog,
  private val bikes: BikeRepository,
  private val clock: Clock,
) : Presenter<RideTestResultUiState> {
  @Composable
  override fun present(): RideTestResultUiState {
    val tests = rememberRideTests(log, clock)
    val test =
      (listOfNotNull(tests.active) + tests.past).firstOrNull {
        it.startedAt == screen.startedAt
      }
    return RideTestResultUiState(test, rememberComponentNames(bikes)) { event ->
      when (event) {
        RideTestResultEvent.Done -> navigator.pop()
      }
    }
  }
}

@CircuitInject(RideTestResultScreen::class, AppScope::class)
@Composable
fun RideTestResultUi(state: RideTestResultUiState, modifier: Modifier = Modifier) {
  val sink = state.eventSink
  val colors = CassetteTheme.colors
  val test = state.test
  DiagnosticsScaffold(
    test?.label?.let { "$it." } ?: "Ride test.",
    onBack = { sink(RideTestResultEvent.Done) },
    modifier,
  ) {
    if (test == null) {
      Text(
        "This test is no longer in the capture log.",
        style = CassetteTheme.type.body,
        color = colors.muted,
      )
      return@DiagnosticsScaffold
    }
    Kicker(
      "Ride test · ${spoken(test.durationMillis)} · ${profileLabel(test.profile)}" +
        if (test.endedAt == null) " · still recording" else ""
    )
    VerdictBlock(test.verdict, verdictSentence(test))
    Panel {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker("When it heard the bike", Modifier.weight(1f))
        if (test.wakes > 1)
          Text(
            "${spoken(test.longestGapMillis)} quiet".uppercase(),
            style = CassetteTheme.type.status,
            color =
              if (test.verdict == RideVerdict.KeptUp) colors.muted
              else colors.status(test.verdict.tone),
          )
      }
      WakeTimeline(
        test.timeline,
        test.durationMillis,
        "Wakes across the ride: ${test.wakes}, longest quiet ${spoken(test.longestGapMillis)}",
        height = 56.dp,
      )
      Row {
        Text("0", style = CassetteTheme.type.mono, color = colors.muted)
        Box(Modifier.weight(1f))
        Text(spoken(test.durationMillis), style = CassetteTheme.type.mono, color = colors.muted)
      }
      Text(
        "Each bar is a slice of the ride; tinted ones are quiet stretches of 45 s or more.",
        style = CassetteTheme.type.bodySmall,
        color = colors.muted,
      )
    }
    if (test.components.isNotEmpty()) {
      Kicker("What it heard", Modifier.padding(top = 4.dp))
      Panel {
        test.components.forEachIndexed { i, component ->
          val name = state.names[component.key]
          HeardRow(
            code = name?.code ?: "?",
            name = name?.name ?: component.key,
            share = if (test.wakes > 0) component.wakesHeardIn.toFloat() / test.wakes else 0f,
            note =
              listOfNotNull(
                  component.readsOk.takeIf { it > 0 }?.let { "$it battery reads" },
                  "quiet at most ${spoken(component.longestSilenceMillis)}",
                )
                .joinToString(" · ")
                .replaceFirstChar { it.uppercase() },
          )
          if (i < test.components.lastIndex) HorizontalDivider(color = colors.line)
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Panel(Modifier.weight(1f)) {
        Figure(
          "Battery reads",
          if (test.gattAttempts == 0) "None" else "${test.gattOk} of ${test.gattAttempts}",
          note = test.gattAverageMillis?.let { "Average ${it / 100 / 10.0} s" } ?: "None were due",
        )
      }
      Panel(Modifier.weight(1f)) {
        Figure(
          "Phone battery",
          test.phoneBatteryUsed?.let(::phoneUsed) ?: "Unknown",
          note = "Over ${spoken(test.durationMillis)}",
        )
      }
    }
    Details(test)
    CassetteButton("Done", onClick = { sink(RideTestResultEvent.Done) })
  }
}

/** What the verdict means for this ride, in a sentence or two. */
internal fun verdictSentence(test: RideSummary): String {
  val reads =
    when {
      test.gattAttempts == 0 -> "No battery reads were due."
      test.gattOk == test.gattAttempts ->
        if (test.gattAttempts == 1) "The battery read worked."
        else "All ${test.gattAttempts} battery reads worked."
      else -> "${test.gattAttempts - test.gattOk} of ${test.gattAttempts} battery reads failed."
    }
  val outOfTime =
    if (test.budgetExceeded > 0)
      " ${test.budgetExceeded} ${if (test.budgetExceeded == 1) "wake" else "wakes"} ran out of " +
        "time before reading."
    else ""
  return when (test.verdict) {
    RideVerdict.KeptUp ->
      "Cassette heard the bike about every ${spoken(roundedSecond(test.durationMillis / test.wakes))} " +
        "and was " +
        "never quiet for more than ${spoken(test.longestGapMillis)}. $reads"
    RideVerdict.Patchy ->
      if (test.longestGapMillis > 2 * 60_000L)
        "Quiet for ${spoken(test.longestGapMillis)} once. If you stopped that long, the bike " +
          "was asleep and that's expected; if you were riding, the scan missed it. $reads$outOfTime"
      else "Cassette kept hearing the bike, but not every read worked. $reads$outOfTime"
    RideVerdict.Lost ->
      if (test.armFailures.isNotEmpty())
        "The background scan couldn't start: ${test.armFailures.first().removePrefix("Not armed: ")}."
      else
        "Cassette never heard the bike. Check it was awake (shift once) and that Background " +
          "capture is on."
  }
}

private fun roundedSecond(millis: Long): Long = (millis + 500) / 1_000 * 1_000

/** The verdict in its status colour, with the verdict's own icon (not the battery ones). */
@Composable
private fun VerdictBlock(verdict: RideVerdict, sentence: String) {
  val block = CassetteTheme.colors.block(verdict.tone)
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(block.container)
      .padding(horizontal = 18.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        verdict.icon,
        contentDescription = null,
        tint = block.content,
        modifier = Modifier.size(30.dp),
      )
      Text(
        verdict.word.uppercase(),
        style =
          CassetteTheme.type.title.copy(fontSize = CassetteTheme.type.numeralLarge.fontSize * 0.5f),
        color = block.content,
      )
    }
    Text(sentence, style = CassetteTheme.type.body, color = block.content)
  }
}

/** A component: its badge, name, share of wakes as a bar, and a note. */
@Composable
private fun HeardRow(code: String, name: String, share: Float, note: String) {
  val colors = CassetteTheme.colors
  Row(
    Modifier.padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CodeBadge(code)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          name,
          style = CassetteTheme.type.itemTitle,
          color = colors.text,
          modifier = Modifier.weight(1f),
        )
        Text(
          "${(share * 100).roundToInt()}% of wakes",
          style = CassetteTheme.type.bodySmall,
          color = colors.muted,
        )
      }
      Box(
        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.track)
      ) {
        Box(
          Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f))
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.text)
        )
      }
      Text(note, style = CassetteTheme.type.bodySmall, color = colors.muted)
    }
  }
}

/** The raw numbers, folded away: what the capture log itself says (decision 0005). */
@Composable
private fun Details(test: RideSummary) {
  val colors = CassetteTheme.colors
  var open by rememberSaveable { mutableStateOf(false) }
  Panel {
    Row(
      Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClickLabel = if (open) "Hide" else "Show") { open = !open }
        .padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text("Details", style = CassetteTheme.type.itemTitle, color = colors.text)
        Text(
          "The raw numbers, as Share diagnostics sends them",
          style = CassetteTheme.type.bodySmall,
          color = colors.muted,
        )
      }
      Icon(
        LucideIcons.ChevronDown,
        contentDescription = null,
        tint = colors.muted,
        modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
      )
    }
    if (open)
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MonoLine("profile", test.profile)
        MonoLine("length", precise(test.durationMillis))
        MonoLine("wakes", "${test.wakes} (${test.wakesPerHour.roundToInt()}/h)")
        MonoLine("longest gap", precise(test.longestGapMillis))
        MonoLine("quiet at end", precise(test.silentTailMillis))
        MonoLine(
          "reads",
          "${test.gattOk}/${test.gattAttempts} ok" +
            (test.gattAverageMillis?.let { ", avg ${it / 100 / 10.0} s" } ?: ""),
        )
        MonoLine("out of time", test.budgetExceeded.toString())
        if (test.armFailures.isNotEmpty())
          MonoLine("arm failed", test.armFailures.distinct().joinToString())
        test.phoneBatteryUsed?.let { MonoLine("phone", "−$it%") }
        test.components.forEach {
          MonoLine(
            it.key.take(14),
            "heard in ${it.wakesHeardIn} · gap ${precise(it.longestSilenceMillis)}" +
              if (it.readsOk > 0) " · ${it.readsOk} reads" else "",
          )
        }
      }
  }
}
