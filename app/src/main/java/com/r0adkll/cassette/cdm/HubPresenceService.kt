package com.r0adkll.cassette.cdm

import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.util.Log
import androidx.annotation.RequiresApi
import com.r0adkll.cassette.CassetteApp
import com.r0adkll.cassette.ridetest.RideEvent
import kotlinx.coroutines.launch

/**
 * CDM spike: the system binds this while an observed association is present (the process needn't be running)
 * and unbinds it a few seconds after the device disappears. Created/destroyed are logged so bind time is visible.
 */
@RequiresApi(36)
class HubPresenceService : CompanionDeviceService() {
  private val app
    get() = application as CassetteApp

  override fun onCreate() {
    super.onCreate()
    app.scope.launch { app.rideLog.append(RideEvent.Cdm(System.currentTimeMillis(), "service_created", detail = "process age ${app.processAgeMillis()} ms")) }
  }

  override fun onDestroy() {
    app.scope.launch { app.rideLog.append(RideEvent.Cdm(System.currentTimeMillis(), "service_destroyed")) }
    super.onDestroy()
  }

  override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
    Log.d(TAG, "Presence ${presenceEventName(event.event)} association ${event.associationId}")
    app.onPresenceEvent(event.associationId, event.event)
  }

  private companion object {
    const val TAG = "CassetteCdm"
  }
}
