package com.r0adkll.cassette.settings.api

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.data.ble.ScanProfile
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** User settings and capture bookkeeping, stored with DataStore Preferences (decision 0007). */
interface CassetteSettings {
  /** Background capture on or off (Settings → Capture). On by default. */
  val backgroundCapture: Flow<Boolean>

  suspend fun setBackgroundCapture(enabled: Boolean)

  /** The background scan configuration (Diagnostics → Scan profile). */
  val scanProfile: Flow<ScanProfile>

  suspend fun setScanProfile(profile: ScanProfile)

  /** A pinned default bike; null follows the most recently seen bike (decision 0010). */
  val pinnedBike: Flow<BikeId?>

  suspend fun setPinnedBike(id: BikeId?)

  val notifications: Flow<NotificationSettings>

  suspend fun setNotifications(settings: NotificationSettings)

  /** Whether first-run onboarding has been done (or skipped because a bike already exists). */
  val onboarded: Flow<Boolean>

  suspend fun setOnboarded()

  /** Tips on the bike screen that have been acted on or dismissed; they don't come back. */
  val dismissedTips: Flow<Set<Tip>>

  suspend fun dismissTip(tip: Tip)

  /** What background capture has been doing, for Diagnostics. */
  val captureHealth: Flow<CaptureHealth>

  suspend fun recordArm(result: String, at: Instant)

  suspend fun recordDelivery(readings: Int, at: Instant)
}

/** The bike screen's tips, offered after onboarding (decision 0011). */
enum class Tip {
  Widget,
  QuickSettingsTile,
}

/** One switch per notification channel (decision 0011). */
data class NotificationSettings(
  val batteryAlerts: Boolean = true,
  val preRide: Boolean = true,
  val notSeenReminders: Boolean = true,
)

data class CaptureHealth(
  /** "Armed", or why the background scan isn't running. */
  val armResult: String?,
  val armedAt: Instant?,
  val lastDeliveryAt: Instant?,
  val deliveries: Long,
)
