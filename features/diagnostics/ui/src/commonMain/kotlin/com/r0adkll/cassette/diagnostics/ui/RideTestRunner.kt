package com.r0adkll.cassette.diagnostics.ui

import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.api.CaptureEvent
import com.r0adkll.cassette.capture.api.CaptureLog
import com.r0adkll.cassette.capture.api.PhoneBattery
import com.r0adkll.cassette.capture.api.activeSession
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.api.CassetteSettings
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

/**
 * Starts and stops ride tests (decision 0009). A test that tries another scan profile switches to
 * it for the ride and puts the everyday one back when it stops; the profile to restore is kept in
 * the session's start event, so it survives the app being killed mid-ride.
 */
@Inject
class RideTestRunner(
  private val log: CaptureLog,
  private val settings: CassetteSettings,
  private val capture: CaptureController,
  private val battery: PhoneBattery,
  private val clock: Clock,
) {
  /** Starts a test with [profile] and arms background capture with it. */
  suspend fun start(label: String, profile: ScanProfile) {
    val usual = settings.scanProfile.first()
    if (profile != usual) settings.setScanProfile(profile)
    log.append(
      CaptureEvent.SessionStarted(
        clock.now().toEpochMilliseconds(),
        label.ifBlank { "Ride" },
        profile.name,
        battery.percent(),
        restoreProfile = usual.name.takeIf { profile != usual },
      )
    )
    capture.ensureBackgroundCapture("ride test started")
  }

  /** Ends the test that's recording, if any, restoring the everyday profile; its start time. */
  suspend fun stop(): Long? {
    val start = log.events.first().activeSession() ?: return null
    log.append(CaptureEvent.SessionEnded(clock.now().toEpochMilliseconds(), battery.percent()))
    val restore = ScanProfile.entries.firstOrNull { it.name == start.restoreProfile }
    if (restore != null) {
      settings.setScanProfile(restore)
      capture.ensureBackgroundCapture("ride test ended")
    }
    return start.at
  }
}
