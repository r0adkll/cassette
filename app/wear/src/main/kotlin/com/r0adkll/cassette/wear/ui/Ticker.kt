package com.r0adkll.cassette.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.delay

/** The time now, updated every [every]: keeps "seen 2 min ago" and countdowns current. */
@Composable
fun rememberNow(clock: Clock, every: Duration): Instant {
  var now by remember { mutableStateOf(clock.now()) }
  LaunchedEffect(every) {
    while (true) {
      now = clock.now()
      delay(every)
    }
  }
  return now
}
