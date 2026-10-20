package com.r0adkll.cassette.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.GattKind
import com.r0adkll.cassette.capture.api.GattOutcome
import com.r0adkll.cassette.capture.api.RideSummary
import com.r0adkll.cassette.capture.api.Sighting
import com.r0adkll.cassette.capture.api.summarizeSessions
import com.r0adkll.cassette.common.compose.AppPermission
import com.r0adkll.cassette.common.compose.AppPlatform
import com.r0adkll.cassette.common.compose.FixedPermissionController
import com.r0adkll.cassette.common.compose.LocalAppPlatform
import com.r0adkll.cassette.common.compose.LocalPermissionController
import com.r0adkll.cassette.common.compose.PermissionState
import com.r0adkll.cassette.common.compose.theme.CassetteTheme
import com.r0adkll.cassette.data.ble.ScanProfile
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the ride test screens (design A, decision 0009) to `build/screens/`, light and dark, from
 * capture-log events, so the timeline and verdicts come from the real summaries.
 */
class RideTestRenderTest {
  private val out = File("build/screens").apply { mkdirs() }
  private val start = 1_792_000_000_000L
  private val now = Instant.fromEpochMilliseconds(start + 14.minutes.inWholeMilliseconds)

  private val names =
    mapOf(
      "rd" to ComponentName("RD", "Rear derailleur"),
      "fd" to ComponentName("FD", "Front derailleur"),
      "l" to ComponentName("L", "Left shifter"),
      "r" to ComponentName("R", "Right shifter"),
      "pm" to ComponentName("PM", "Power meter"),
    )

  /**
   * A ride like the validation rides (decision 0009): a wake about every 3.6 s, one quiet stretch
   * of [quietMillis] at a third of the way, background reads every couple of minutes.
   */
  private fun ride(
    label: String,
    at: Long,
    minutes: Int,
    lengthMillis: Long = minutes * 60_000L,
    quietMillis: Long = 46_000,
    failedReads: Int = 0,
    profile: ScanProfile = ScanProfile.AllLowPower,
    ended: Boolean = true,
  ): List<CaptureEvent> {
    val length = lengthMillis
    val quietFrom = at + length / 3
    val events =
      mutableListOf<CaptureEvent>(CaptureEvent.SessionStarted(at, label, profile.name, 80))
    var t = at + 2_000
    var i = 0
    while (t < at + length) {
      if (t in quietFrom until quietFrom + quietMillis) {
        t = quietFrom + quietMillis
        continue
      }
      val keys = buildList {
        if (i % 13 != 0) add("rd")
        if (i % 3 == 0) add("fd")
        if (i % 5 == 0) add("l")
        if (i % 3 == 1) add("r")
        if (i % 10 < 7) add("pm")
      }
      events += CaptureEvent.Delivery(t, keys.map { Sighting(it, "STATUS", t, -60) }, 0)
      if (i % 40 == 20) {
        val key = if (i % 80 == 20) "rd" else "fd"
        val ok = events.count { it is CaptureEvent.Gatt } >= failedReads
        events +=
          CaptureEvent.Gatt(
            t + 500,
            key,
            GattKind.HubTable,
            background = true,
            durationMillis = 3_100,
            outcome = if (ok) GattOutcome.Ok else GattOutcome.Failed,
          )
      }
      t += 3_600
      i++
    }
    if (ended) events += CaptureEvent.SessionEnded(at + length, 79)
    return events
  }

  private fun summaries(events: List<CaptureEvent>, nowMillis: Long = now.toEpochMilliseconds()) =
    summarizeSessions(events, nowMillis)

  private val commute: RideSummary
    get() = summaries(ride("Commute", start, 14)).single()

  @Test
  fun rideTests() {
    val history =
      summaries(
        ride("Commute", start, 14) +
          ride("Lunch loop", start - 2 * 86_400_000L, 15) +
          ride(
            "Gravel loop",
            start - 3 * 86_400_000L,
            40,
            quietMillis = 9 * 60_000L,
            failedReads = 1,
          ),
        nowMillis = start + 15.minutes.inWholeMilliseconds,
      )
    val active =
      summaries(
          ride("Commute", start, 14, lengthMillis = 548_000, ended = false),
          nowMillis = start + 552_000,
        )
        .single()
    val lost =
      summaries(
          listOf(
            CaptureEvent.SessionStarted(start, "Short loop", "AllLowPower", 64),
            CaptureEvent.Armed(
              start + 1,
              "AllLowPower",
              "Not armed: Bluetooth is off",
              "ride test started",
              ok = false,
            ),
            CaptureEvent.SessionEnded(start + 20 * 60_000L, 63),
          )
        )
        .single()
    val shots = buildList {
      add(Shot("ride-tests") { RideTestsUi(historyState(active = null, past = history)) })
      add(Shot("ride-tests-empty") { RideTestsUi(historyState(active = null, past = emptyList())) })
      add(Shot("ride-tests-recording-row") { RideTestsUi(historyState(active, history.drop(1))) })
      // As it opens: no name yet, so Next waits.
      add(
        Shot("ride-test-setup-empty") { RideTestSetupUi(setupState(ScanProfile.AllLowPower, "")) }
      )
      add(Shot("ride-test-setup") { RideTestSetupUi(setupState(ScanProfile.AllLowPower)) })
      add(Shot("ride-test-setup-batched") { RideTestSetupUi(setupState(ScanProfile.Batched30s)) })
      add(Shot("ride-test-checks") { RideTestSetupUi(checksState(ready = true)) })
      add(Shot("ride-test-checks-not-ready") { RideTestSetupUi(checksState(ready = false)) })
      add(
        Shot("ride-test-recording") {
          RideTestRecordingUi(RideTestRecordingUiState(active, null, false) {})
        }
      )
      add(
        Shot("ride-test-recording-batched") {
          RideTestRecordingUi(RideTestRecordingUiState(active, "Low power", false) {})
        }
      )
      add(
        Shot("ride-test-result", tall = true) {
          RideTestResultUi(RideTestResultUiState(commute, names) {})
        }
      )
      add(
        Shot("ride-test-result-patchy", tall = true) {
          RideTestResultUi(RideTestResultUiState(history.last(), names) {})
        }
      )
      add(
        Shot("ride-test-result-lost", tall = true) {
          RideTestResultUi(RideTestResultUiState(lost, names) {})
        }
      )
    }
    for (dark in listOf(true, false)) {
      val theme = if (dark) "dark" else "light"
      for (shot in shots) render(
        "${shot.name}-$theme",
        dark,
        AppPlatform.Android,
        shot.tall,
        shot.content,
      )
      // iOS: no profile choice or comparison (decision 0017).
      render("ride-tests-ios-$theme", dark, AppPlatform.Ios) {
        RideTestsUi(historyState(active = null, past = history))
      }
      render("ride-test-setup-ios-$theme", dark, AppPlatform.Ios) {
        RideTestSetupUi(setupState(ScanProfile.AllLowPower))
      }
    }
  }

  private class Shot(
    val name: String,
    val tall: Boolean = false,
    val content: @Composable () -> Unit,
  )

  private fun historyState(active: RideSummary?, past: List<RideSummary>) =
    RideTestsUiState(
      active = active,
      past = past,
      comparison = compareProfiles(past),
      now = Instant.fromEpochMilliseconds(start + 15.minutes.inWholeMilliseconds),
      confirmClear = false,
    ) {}

  private fun setupState(
    profile: ScanProfile,
    label: String = "Commute",
    step: Int = 1,
    ready: Boolean = true,
  ) =
    RideTestSetupUiState(
      step = step,
      usual = ScanProfile.AllLowPower,
      profile = profile,
      label = label,
      checks =
        ReadyChecks(
          bluetoothReady = ready,
          backgroundCapture = ready,
          bikeName = "Road bike",
          bikeLastSeen = if (ready) now - 2.minutes else null,
          now = now,
        ),
      starting = false,
    ) {}

  private fun checksState(ready: Boolean) =
    setupState(ScanProfile.AllLowPower, step = 2, ready = ready)

  private fun render(
    name: String,
    dark: Boolean,
    platform: AppPlatform,
    tall: Boolean = false,
    content: @Composable () -> Unit,
  ) {
    val permissions =
      FixedPermissionController(
        AppPermission.entries.associateWith {
          if (name.contains("not-ready") && it == AppPermission.Nearby) PermissionState.Blocked
          else PermissionState.Granted
        }
      )
    val height = if (tall) 1_640 else 892
    val scene =
      ImageComposeScene(width = 412 * 2, height = height * 2, density = Density(2f)) {
        CassetteTheme(darkTheme = dark) {
          CompositionLocalProvider(
            LocalAppPlatform provides platform,
            LocalPermissionController provides permissions,
          ) {
            Box(Modifier.fillMaxSize().background(CassetteTheme.colors.background)) { content() }
          }
        }
      }
    repeat(4) { scene.render(it * 16_000_000L) }
    File(out, "$name.png")
      .writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }
}
