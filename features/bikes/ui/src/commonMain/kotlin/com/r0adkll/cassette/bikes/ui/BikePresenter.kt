package com.r0adkll.cassette.bikes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.api.BikeOverview
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.bikes.api.DeveloperTools
import com.r0adkll.cassette.common.screens.AddBikeScreen
import com.r0adkll.cassette.common.screens.BikeScreen
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.r0adkll.cassette.common.screens.DesignGalleryScreen
import com.r0adkll.cassette.core.BikeId
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@CircuitInject(BikeScreen::class, AppScope::class)
@Inject
class BikePresenter(
    private val screen: BikeScreen,
    private val navigator: Navigator,
    private val repository: BikeRepository,
    private val developerTools: DeveloperTools,
    private val clock: Clock,
) : Presenter<BikeUiState> {

    @Composable
    override fun present(): BikeUiState {
        val scope = rememberCoroutineScope()
        val overview by
            remember(screen.bikeId) {
                    repository.observeOverview(screen.bikeId?.let(::BikeId)).map { Result(it) }
                }
                .collectAsRetainedState(initial = null)

        // "Seen 2 min ago" keeps itself current.
        var now by remember { mutableStateOf(clock.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30.seconds)
                now = clock.now()
            }
        }

        val eventSink: (BikeEvent) -> Unit = { event ->
            when (event) {
                is BikeEvent.OpenComponent -> navigator.goTo(ComponentScreen(event.id))
                // Settings arrives with the settings feature; the gallery stands in for now.
                BikeEvent.OpenSettings -> navigator.goTo(DesignGalleryScreen)
                BikeEvent.AddBike -> navigator.goTo(AddBikeScreen)
                // Refresh needs capture, which is ported next.
                BikeEvent.Refresh -> Unit
                BikeEvent.LoadSampleBike -> scope.launch { developerTools.loadSampleBike() }
            }
        }

        val loaded = overview ?: return BikeUiState.Loading(eventSink)
        val bike = loaded.overview ?: return BikeUiState.Empty(eventSink)
        return bike.toLoaded(now, eventSink)
    }

    /** Wraps the nullable overview so "loading" and "no bike" are different states. */
    private data class Result(val overview: BikeOverview?)
}
