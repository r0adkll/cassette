package com.r0adkll.cassette.settings.test

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.api.CaptureHealth
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.NotificationSettings
import com.r0adkll.cassette.settings.api.ThemeMode
import com.r0adkll.cassette.settings.api.Tip
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Settings for tests, with the same defaults as the real store. */
class InMemorySettings : CassetteSettings {
  override val backgroundCapture = MutableStateFlow(true)
  override val scanProfile = MutableStateFlow(ScanProfile.AllLowPower)
  override val pinnedBike = MutableStateFlow<BikeId?>(null)
  override val notifications = MutableStateFlow(NotificationSettings())
  override val captureHealth = MutableStateFlow(CaptureHealth(null, null, null, 0))
  override val onboarded = MutableStateFlow(false)
  override val dismissedTips = MutableStateFlow(emptySet<Tip>())
  override val themeMode = MutableStateFlow(ThemeMode.System)

  override suspend fun setThemeMode(mode: ThemeMode) {
    themeMode.value = mode
  }

  override val playfulTitles = MutableStateFlow(true)

  override suspend fun setPlayfulTitles(enabled: Boolean) {
    playfulTitles.value = enabled
  }

  override suspend fun dismissTip(tip: Tip) {
    dismissedTips.update { it + tip }
  }

  override suspend fun setOnboarded() {
    onboarded.value = true
  }

  override suspend fun setBackgroundCapture(enabled: Boolean) {
    backgroundCapture.value = enabled
  }

  override suspend fun setScanProfile(profile: ScanProfile) {
    scanProfile.value = profile
  }

  override suspend fun setPinnedBike(id: BikeId?) {
    pinnedBike.value = id
  }

  override suspend fun setNotifications(settings: NotificationSettings) {
    notifications.value = settings
  }

  override suspend fun recordArm(result: String, at: Instant) {
    captureHealth.update { it.copy(armResult = result, armedAt = at) }
  }

  override suspend fun recordDelivery(readings: Int, at: Instant) {
    captureHealth.update { it.copy(lastDeliveryAt = at, deliveries = it.deliveries + 1) }
  }
}
