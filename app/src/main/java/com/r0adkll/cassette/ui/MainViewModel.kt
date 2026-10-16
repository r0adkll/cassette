package com.r0adkll.cassette.ui

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r0adkll.cassette.CassetteApp
import com.r0adkll.cassette.cdm.Association
import com.r0adkll.cassette.cdm.AssociationFilter
import com.r0adkll.cassette.cdm.PresenceRow
import com.r0adkll.cassette.cdm.presenceTimeline
import com.r0adkll.cassette.data.CaptureSource
import com.r0adkll.cassette.data.CaptureState
import com.r0adkll.cassette.ridetest.PresenceReadPath
import com.r0adkll.cassette.ridetest.RideEvent
import com.r0adkll.cassette.ridetest.RideSummary
import com.r0adkll.cassette.ridetest.activeSession
import com.r0adkll.cassette.ridetest.alwaysLogged
import com.r0adkll.cassette.ridetest.summarizeSessions
import com.r0adkll.cassette.scan.NearbyCandidate
import com.r0adkll.cassette.scan.ScanProfile
import com.r0adkll.cassette.scan.toCandidate
import com.r0adkll.cassette.scan.toReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(private val app: CassetteApp) : ViewModel() {
  val capture: StateFlow<CaptureState?> = app.store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _liveScan = MutableStateFlow<LiveScan>(LiveScan.Idle)
  val liveScan: StateFlow<LiveScan> = _liveScan.asStateFlow()

  private val _candidates = MutableStateFlow<Map<String, NearbyCandidate>>(emptyMap())
  /** SRAM-looking adverts from the current session that didn't decode as AXS battery broadcasts. */
  val candidates: StateFlow<Map<String, NearbyCandidate>> = _candidates.asStateFlow()

  private var liveJob: Job? = null

  private val ticker = flow {
    while (true) {
      emit(Unit)
      delay(5_000)
    }
  }

  /** Ride tests: the running session (if any) and summaries of all sessions, refreshed every few seconds. */
  val rideTests: StateFlow<RideTestState> =
    combine(app.rideLog.events, ticker) { events, _ ->
        RideTestState(active = events.activeSession(), summaries = summarizeSessions(events, System.currentTimeMillis()))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RideTestState(active = null, summaries = emptyList()))

  /** CDM spike card state; null below API 36. */
  val cdm: StateFlow<CdmSpikeState?> =
    (app.presence?.let { presence ->
        combine(presence.associations, presence.observing, presence.readPath, app.rideLog.events, ticker) { associations, observing, path, events, _ ->
          CdmSpikeState(
            associations = associations,
            observing = observing,
            readPath = path,
            timeline = presenceTimeline(events),
            recentActions = events.filterIsInstance<RideEvent.Cdm>().takeLast(5).asReversed(),
            hubAddresses = events.filterIsInstance<RideEvent.HubAddress>().associateBy { it.key }.values.sortedByDescending { it.at },
          )
        }
      } ?: flowOf(null))
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _chooser = Channel<IntentSender>(Channel.BUFFERED)
  /** The CDM association chooser to launch from the activity. */
  val chooser: Flow<IntentSender> = _chooser.receiveAsFlow()

  fun linkHub(filter: AssociationFilter) {
    app.presence?.associate(filter) { _chooser.trySend(it) }
  }

  fun onChooserResult(resultCode: Int) {
    app.presence?.onChooserResult(resultCode)
  }

  fun setObserving(associationId: Int, observing: Boolean) {
    val presence = app.presence ?: return
    if (observing) presence.startObserving(associationId) else presence.stopObserving(associationId)
  }

  fun unlinkHub(associationId: Int) {
    app.presence?.disassociate(associationId)
  }

  fun setPresenceReadPath(path: PresenceReadPath) {
    app.presence?.setReadPath(path)
  }

  fun refreshAssociations() {
    app.presence?.refresh()
  }

  fun clearCdmLog() {
    viewModelScope.launch { app.rideLog.clear { it.alwaysLogged } }
  }

  fun startRideTest(label: String, profile: ScanProfile) {
    viewModelScope.launch { app.startRideTest(label.ifBlank { "Untitled ride" }, profile) }
  }

  fun stopRideTest() {
    viewModelScope.launch { app.stopRideTest() }
  }

  fun clearRideTests() {
    viewModelScope.launch { app.rideLog.clear { !it.alwaysLogged } }
  }

  fun toggleLiveScan() {
    if (liveJob?.isActive == true) {
      liveJob?.cancel()
      _liveScan.value = LiveScan.Idle
      return
    }
    _liveScan.value = LiveScan.Scanning
    liveJob =
      viewModelScope.launch {
        app.scanner
          .liveResults()
          .catch { _liveScan.value = LiveScan.Failed(it.message ?: "Scan failed") }
          .collect { result ->
            val reading = result.toReading()
            if (reading != null) {
              app.store.record(listOf(reading), CaptureSource.LIVE)
              launch { app.refreshBatteries(listOf(reading), CaptureSource.LIVE) }
            } else {
              result.toCandidate()?.let { candidate -> _candidates.update { it + (candidate.address to candidate) } }
            }
          }
      }
  }

  fun setBackgroundCapture(enabled: Boolean) {
    viewModelScope.launch { app.setBackgroundCapture(enabled, reason = "toggled") }
  }

  fun onPermissionGranted() {
    viewModelScope.launch { app.rearmBackgroundIfEnabled(reason = "permission granted") }
  }

  fun setLabel(key: String, label: String?) {
    viewModelScope.launch { app.store.setLabel(key, label) }
  }

  fun clearReadings() {
    _candidates.value = emptyMap()
    viewModelScope.launch { app.store.clearReadings() }
  }
}

data class CdmSpikeState(
  val associations: List<Association>,
  val observing: Set<Int>,
  val readPath: PresenceReadPath,
  val timeline: List<PresenceRow>,
  val recentActions: List<RideEvent.Cdm>,
  /** The latest logged scanned address per hub. */
  val hubAddresses: List<RideEvent.HubAddress>,
)

data class RideTestState(val active: RideEvent.SessionStarted?, val summaries: List<RideSummary>)

sealed interface LiveScan {
  data object Idle : LiveScan

  data object Scanning : LiveScan

  data class Failed(val message: String) : LiveScan
}
