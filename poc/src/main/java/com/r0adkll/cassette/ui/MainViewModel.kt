package com.r0adkll.cassette.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.r0adkll.cassette.CassetteApp
import com.r0adkll.cassette.data.CaptureSource
import com.r0adkll.cassette.data.CaptureState
import com.r0adkll.cassette.ridetest.RideEvent
import com.r0adkll.cassette.ridetest.RideSummary
import com.r0adkll.cassette.ridetest.activeSession
import com.r0adkll.cassette.ridetest.summarizeSessions
import com.r0adkll.cassette.scan.NearbyCandidate
import com.r0adkll.cassette.scan.ScanProfile
import com.r0adkll.cassette.scan.toCandidate
import com.r0adkll.cassette.scan.toReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(private val app: CassetteApp) : ViewModel() {
    val capture: StateFlow<CaptureState?> =
        app.store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _liveScan = MutableStateFlow<LiveScan>(LiveScan.Idle)
    val liveScan: StateFlow<LiveScan> = _liveScan.asStateFlow()

    private val _candidates = MutableStateFlow<Map<String, NearbyCandidate>>(emptyMap())
    /**
     * SRAM-looking adverts from the current session that didn't decode as AXS battery broadcasts.
     */
    val candidates: StateFlow<Map<String, NearbyCandidate>> = _candidates.asStateFlow()

    private var liveJob: Job? = null

    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(5_000)
        }
    }

    /**
     * Ride tests: the running session (if any) and summaries of all sessions, refreshed every few
     * seconds.
     */
    val rideTests: StateFlow<RideTestState> =
        combine(app.rideLog.events, ticker) { events, _ ->
                RideTestState(
                    active = events.activeSession(),
                    summaries = summarizeSessions(events, System.currentTimeMillis()),
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                RideTestState(active = null, summaries = emptyList()),
            )

    fun startRideTest(label: String, profile: ScanProfile) {
        viewModelScope.launch { app.startRideTest(label.ifBlank { "Untitled ride" }, profile) }
    }

    fun stopRideTest() {
        viewModelScope.launch { app.stopRideTest() }
    }

    fun clearRideTests() {
        viewModelScope.launch { app.rideLog.clear() }
    }

    fun toggleLiveScan() {
        if (liveJob?.isActive == true) {
            liveJob?.cancel()
            _liveScan.value = LiveScan.Idle
            return
        }
        _liveScan.value = LiveScan.Scanning
        liveJob = viewModelScope.launch {
            app.scanner
                .liveResults()
                .catch { _liveScan.value = LiveScan.Failed(it.message ?: "Scan failed") }
                .collect { result ->
                    val reading = result.toReading()
                    if (reading != null) {
                        app.store.record(listOf(reading), CaptureSource.LIVE)
                        launch { app.refreshBatteries(listOf(reading), CaptureSource.LIVE) }
                    } else {
                        result.toCandidate()?.let { candidate ->
                            _candidates.update { it + (candidate.address to candidate) }
                        }
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

data class RideTestState(val active: RideEvent.SessionStarted?, val summaries: List<RideSummary>)

sealed interface LiveScan {
    data object Idle : LiveScan

    data object Scanning : LiveScan

    data class Failed(val message: String) : LiveScan
}
