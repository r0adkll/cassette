package com.r0adkll.cassette.settings.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.api.CaptureHealth
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.api.NotificationSettings
import com.r0adkll.cassette.settings.api.ThemeMode
import com.r0adkll.cassette.settings.api.Tip
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** Where the platform keeps the settings file: an absolute path ending in `.preferences_pb`. */
class SettingsFile(val path: String)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DataStoreSettings(file: SettingsFile) : CassetteSettings {
  private val store: DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { file.path.toPath() })

  override val backgroundCapture: Flow<Boolean> = store.data.map { it[BACKGROUND] ?: true }

  override suspend fun setBackgroundCapture(enabled: Boolean) {
    store.edit { it[BACKGROUND] = enabled }
  }

  /** All matches, low power: validated on two rides, no gaps over 46 s (decision 0009). */
  override val scanProfile: Flow<ScanProfile> =
    store.data.map { prefs ->
      ScanProfile.entries.firstOrNull { it.name == prefs[PROFILE] } ?: ScanProfile.AllLowPower
    }

  override suspend fun setScanProfile(profile: ScanProfile) {
    store.edit { it[PROFILE] = profile.name }
  }

  override val pinnedBike: Flow<BikeId?> = store.data.map { it[PINNED_BIKE]?.let(::BikeId) }

  override suspend fun setPinnedBike(id: BikeId?) {
    store.edit { if (id == null) it.remove(PINNED_BIKE) else it[PINNED_BIKE] = id.value }
  }

  override val notifications: Flow<NotificationSettings> =
    store.data.map {
      NotificationSettings(
        batteryAlerts = it[ALERTS] ?: true,
        preRide = it[PRE_RIDE] ?: true,
        notSeenReminders = it[REMINDERS] ?: true,
      )
    }

  override suspend fun setNotifications(settings: NotificationSettings) {
    store.edit {
      it[ALERTS] = settings.batteryAlerts
      it[PRE_RIDE] = settings.preRide
      it[REMINDERS] = settings.notSeenReminders
    }
  }

  override val themeMode: Flow<ThemeMode> =
    store.data.map { prefs ->
      ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeMode.System
    }

  override suspend fun setThemeMode(mode: ThemeMode) {
    store.edit { it[THEME] = mode.name }
  }

  override val onboarded: Flow<Boolean> = store.data.map { it[ONBOARDED] ?: false }

  override suspend fun setOnboarded() {
    store.edit { it[ONBOARDED] = true }
  }

  override val dismissedTips: Flow<Set<Tip>> =
    store.data.map { prefs ->
      prefs[DISMISSED_TIPS]
        .orEmpty()
        .mapNotNull { name -> Tip.entries.find { it.name == name } }
        .toSet()
    }

  override suspend fun dismissTip(tip: Tip) {
    store.edit { it[DISMISSED_TIPS] = it[DISMISSED_TIPS].orEmpty() + tip.name }
  }

  override val captureHealth: Flow<CaptureHealth> =
    store.data.map {
      CaptureHealth(
        armResult = it[ARM_RESULT],
        armedAt = it[ARMED_AT]?.let(Instant::fromEpochMilliseconds),
        lastDeliveryAt = it[LAST_DELIVERY]?.let(Instant::fromEpochMilliseconds),
        deliveries = it[DELIVERIES] ?: 0,
      )
    }

  override suspend fun recordArm(result: String, at: Instant) {
    store.edit {
      it[ARM_RESULT] = result
      it[ARMED_AT] = at.toEpochMilliseconds()
    }
  }

  override suspend fun recordDelivery(readings: Int, at: Instant) {
    store.edit {
      it[LAST_DELIVERY] = at.toEpochMilliseconds()
      it[DELIVERIES] = (it[DELIVERIES] ?: 0) + 1
    }
  }

  private companion object {
    val BACKGROUND = booleanPreferencesKey("background_capture")
    val PROFILE = stringPreferencesKey("scan_profile")
    val THEME = stringPreferencesKey("theme_mode")
    val PINNED_BIKE = longPreferencesKey("pinned_bike")
    val ONBOARDED = booleanPreferencesKey("onboarded")
    val DISMISSED_TIPS = stringSetPreferencesKey("dismissed_tips")
    val ALERTS = booleanPreferencesKey("notify_battery_alerts")
    val PRE_RIDE = booleanPreferencesKey("notify_pre_ride")
    val REMINDERS = booleanPreferencesKey("notify_not_seen")
    val ARM_RESULT = stringPreferencesKey("capture_arm_result")
    val ARMED_AT = longPreferencesKey("capture_armed_at")
    val LAST_DELIVERY = longPreferencesKey("capture_last_delivery")
    val DELIVERIES = longPreferencesKey("capture_deliveries")
  }
}
