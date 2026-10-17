package com.r0adkll.cassette.settings.impl

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.data.ble.ScanProfile
import com.r0adkll.cassette.settings.api.NotificationSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DataStoreSettingsTest {
  private val file = File.createTempFile("settings", ".preferences_pb").apply { delete() }

  @Test
  fun defaultsAndRoundTrips() = runTest {
    val settings = DataStoreSettings(SettingsFile(file.absolutePath))

    assertTrue(settings.backgroundCapture.first())
    assertEquals(ScanProfile.AllLowPower, settings.scanProfile.first())
    assertNull(settings.pinnedBike.first())
    assertEquals(NotificationSettings(), settings.notifications.first())

    settings.setBackgroundCapture(false)
    settings.setScanProfile(ScanProfile.Batched30s)
    settings.setPinnedBike(BikeId(3))
    settings.setNotifications(NotificationSettings(preRide = false))
    settings.recordDelivery(4, Instant.fromEpochMilliseconds(1_000))
    settings.recordDelivery(2, Instant.fromEpochMilliseconds(2_000))

    assertEquals(false, settings.backgroundCapture.first())
    assertEquals(ScanProfile.Batched30s, settings.scanProfile.first())
    assertEquals(BikeId(3), settings.pinnedBike.first())
    assertEquals(false, settings.notifications.first().preRide)
    val health = settings.captureHealth.first()
    assertEquals(2, health.deliveries)
    assertEquals(2_000, health.lastDeliveryAt?.toEpochMilliseconds())

    settings.setPinnedBike(null)
    assertNull(settings.pinnedBike.first())
  }
}
