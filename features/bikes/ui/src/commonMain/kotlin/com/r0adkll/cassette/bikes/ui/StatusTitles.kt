package com.r0adkll.cassette.bikes.ui

import com.r0adkll.cassette.bikes.summary.BikeStatus
import com.r0adkll.cassette.common.compose.theme.StatusTone
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The playful titles each status rotates through alongside its plain one (decision 0015). Funnier
 * when things are fine, plainer as they get urgent; "No readings yet." and "Status unknown." have
 * none.
 */
internal val PlayfulTitles: Map<StatusTone, List<String>> =
  mapOf(
    StatusTone.Good to
      listOf(
        "No single-speed today.",
        "Fully wound.",
        "Charged in every gear.",
        "Full tape. Press play.",
        "Wireless, not careless.",
        "Send it.",
      ),
    StatusTone.Low to
      listOf(
        "Side A's nearly done.",
        "Charge it tonight.",
        "Plug in after this one.",
        "Getting low.",
      ),
    StatusTone.Critical to
      listOf("Single-speed incoming.", "The tape's running out.", "Charge before you ride."),
    StatusTone.Stale to listOf("Wake me up.", "Gathering dust?", "Shift once to check in."),
  )

/**
 * The bike screen's status title (decision 0015). Each status picks from its plain title and its
 * playful ones once per [session], so the title never changes while the screen is open, and never
 * picks the same title twice in a row. Only the bike screen uses this: `BikeSummary` keeps the
 * plain titles for every other surface.
 */
@SingleIn(AppScope::class)
class StatusTitles(private val random: Random) {
  @Inject constructor() : this(Random.Default)

  private val sessionFlow = MutableStateFlow(0)

  /** Moves on each time the app leaves the foreground; the screen reads it to redraw its title. */
  val session: StateFlow<Int> = sessionFlow

  /** Per status: the session its pick was made in, and the pick (0 is the plain title). */
  private val picks = mutableMapOf<StatusTone, Pair<Int, Int>>()

  /** The app left the foreground: the next time it shows, every status picks again. */
  fun appBackgrounded() {
    sessionFlow.update { it + 1 }
  }

  fun title(status: BikeStatus, session: Int = this.session.value): String {
    val playful = PlayfulTitles[status.tone] ?: return status.headline
    val last = picks[status.tone]
    val pick =
      if (last != null && last.first == session) last.second
      else pickIndex(playful.size + 1, previous = last?.second, random)
    picks[status.tone] = session to pick
    return if (pick == 0) status.headline else playful[pick - 1]
  }
}

/** An index below [count], never [previous] (when there's another to pick). */
internal fun pickIndex(count: Int, previous: Int?, random: Random): Int {
  if (previous == null || count < 2) return random.nextInt(count)
  val pick = random.nextInt(count - 1)
  return if (pick >= previous) pick + 1 else pick
}
