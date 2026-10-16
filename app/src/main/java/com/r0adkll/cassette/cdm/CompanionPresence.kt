package com.r0adkll.cassette.cdm

import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.r0adkll.cassette.ridetest.AssociatedAddress
import com.r0adkll.cassette.ridetest.PresenceReadPath
import com.r0adkll.cassette.ridetest.RideEvent
import com.r0adkll.cassette.ridetest.RideLog
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One CompanionDeviceManager association, as the spike shows it. [address] is upper case, like scan results. */
data class Association(val id: Int, val displayName: String?, val address: String?)

/** Which advert fields the association chooser filters on. Two fields in one filter must both match. */
enum class AssociationFilter(val label: String) {
  UUID_AND_NAME("0xFE51 + name"),
  UUID_ONLY("0xFE51 only"),
  NAME_ONLY("Name only"),
}

/**
 * CDM spike: associates AXS hubs (no bonding) and observes their presence with the API 36 request-based
 * presence API. Presence events arrive in [HubPresenceService]. The system persists observation across
 * reboots; [observing] is our own record of what we asked for, since CDM can't be queried for it.
 */
@RequiresApi(36)
class CompanionPresence(private val context: Context, private val scope: CoroutineScope, private val rideLog: RideLog) {
  private val cdm = context.getSystemService(CompanionDeviceManager::class.java)
  private val prefs = context.getSharedPreferences("cdm-spike", Context.MODE_PRIVATE)

  private val _associations = MutableStateFlow(load())
  val associations: StateFlow<List<Association>> = _associations.asStateFlow()

  private val _observing = MutableStateFlow(prefs.getStringSet(KEY_OBSERVING, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet())
  val observing: StateFlow<Set<Int>> = _observing.asStateFlow()

  private val _readPath = MutableStateFlow(prefs.getString(KEY_READ_PATH, null)?.let { runCatching { PresenceReadPath.valueOf(it) }.getOrNull() } ?: PresenceReadPath.BOUND_SERVICE)
  /** Where the hub read after "appeared" runs; switchable so both paths can be compared. */
  val readPath: StateFlow<PresenceReadPath> = _readPath.asStateFlow()

  fun setReadPath(path: PresenceReadPath) {
    prefs.edit { putString(KEY_READ_PATH, path.name) }
    _readPath.value = path
  }

  fun refresh(): List<Association> = load().also { _associations.value = it }

  fun addressFor(associationId: Int): String? = refresh().firstOrNull { it.id == associationId }?.address

  fun associatedAddresses(): List<AssociatedAddress> = refresh().map { AssociatedAddress(it.id, it.address) }

  /**
   * Shows the system chooser for AXS hubs. [onChooser] gets the IntentSender to launch from the activity;
   * a created association is observed straight away.
   */
  fun associate(filter: AssociationFilter, onChooser: (IntentSender) -> Unit) {
    val scanFilter = ScanFilter.Builder().apply { if (filter != AssociationFilter.NAME_ONLY) setServiceUuid(HUB_SERVICE_UUID) }.build()
    val deviceFilter =
      BluetoothLeDeviceFilter.Builder()
        .setScanFilter(scanFilter)
        .apply { if (filter != AssociationFilter.UUID_ONLY) setNamePattern(HUB_NAME) }
        .build()
    // No device profile: we only want presence, not any of the profile privileges.
    val request = AssociationRequest.Builder().addDeviceFilter(deviceFilter).setSingleDevice(false).build()
    log(RideEvent.Cdm(System.currentTimeMillis(), "associate_requested", detail = filter.name))
    cdm.associate(
      request,
      context.mainExecutor,
      object : CompanionDeviceManager.Callback() {
        override fun onAssociationPending(intentSender: IntentSender) = onChooser(intentSender)

        override fun onAssociationCreated(associationInfo: AssociationInfo) {
          log(RideEvent.Cdm(System.currentTimeMillis(), "associated", associationInfo.id, "${associationInfo.displayName} ${associationInfo.address()}"))
          refresh()
          startObserving(associationInfo.id)
        }

        override fun onFailure(errorCode: Int, error: CharSequence?) {
          log(RideEvent.Cdm(System.currentTimeMillis(), "associate_failed", detail = "$errorCode $error"))
        }

        override fun onFailure(error: CharSequence?) {
          log(RideEvent.Cdm(System.currentTimeMillis(), "associate_failed", detail = error?.toString()))
        }
      },
    )
  }

  /** The chooser activity finished; the association itself arrives in onAssociationCreated. */
  fun onChooserResult(resultCode: Int) {
    if (resultCode != android.app.Activity.RESULT_OK) log(RideEvent.Cdm(System.currentTimeMillis(), "chooser_closed", detail = "result $resultCode"))
    refresh()
  }

  fun startObserving(associationId: Int) {
    val result = runCatching { cdm.startObservingDevicePresence(request(associationId)) }
    result.onSuccess { setObserving(associationId, true) }
    log(RideEvent.Cdm(System.currentTimeMillis(), if (result.isSuccess) "observe_started" else "observe_failed", associationId, result.exceptionOrNull()?.toString()))
  }

  fun stopObserving(associationId: Int) {
    val result = runCatching { cdm.stopObservingDevicePresence(request(associationId)) }
    setObserving(associationId, false)
    log(RideEvent.Cdm(System.currentTimeMillis(), if (result.isSuccess) "observe_stopped" else "observe_stop_failed", associationId, result.exceptionOrNull()?.toString()))
  }

  fun disassociate(associationId: Int) {
    stopObserving(associationId)
    val result = runCatching { cdm.disassociate(associationId) }
    log(RideEvent.Cdm(System.currentTimeMillis(), if (result.isSuccess) "disassociated" else "disassociate_failed", associationId, result.exceptionOrNull()?.toString()))
    refresh()
  }

  private fun setObserving(associationId: Int, observing: Boolean) {
    val updated = if (observing) _observing.value + associationId else _observing.value - associationId
    prefs.edit { putStringSet(KEY_OBSERVING, updated.map { it.toString() }.toSet()) }
    _observing.value = updated
  }

  private fun request(associationId: Int) = ObservingDevicePresenceRequest.Builder().setAssociationId(associationId).build()

  private fun load(): List<Association> =
    runCatching { cdm.myAssociations }
      .onFailure { Log.w(TAG, "Couldn't list associations", it) }
      .getOrDefault(emptyList())
      .map { Association(it.id, it.displayName?.toString(), it.address()) }

  private fun log(event: RideEvent.Cdm) {
    Log.d(TAG, "CDM ${event.action} ${event.associationId ?: ""} ${event.detail ?: ""}")
    scope.launch { rideLog.append(event) }
  }

  private fun AssociationInfo.address(): String? = deviceMacAddress?.toString()?.uppercase()

  private companion object {
    const val TAG = "CassetteCdm"
    const val KEY_OBSERVING = "observing"
    const val KEY_READ_PATH = "read_path"
    val HUB_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000fe51-0000-1000-8000-00805f9b34fb")
    val HUB_NAME: Pattern = Pattern.compile("^SRAM \\d+$")
  }
}
