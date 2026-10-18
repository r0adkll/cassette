package com.r0adkll.cassette.bikes.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.r0adkll.cassette.bikes.api.BikeRepository
import com.r0adkll.cassette.common.screens.ComponentScreen
import com.r0adkll.cassette.core.ComponentId
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.collectAsRetainedState
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@CircuitInject(ComponentScreen::class, AppScope::class)
@Inject
class ComponentPresenter(
  private val screen: ComponentScreen,
  private val navigator: Navigator,
  private val repository: BikeRepository,
  private val clock: Clock,
) : Presenter<ComponentUiState> {

  @Composable
  override fun present(): ComponentUiState {
    var range by rememberRetained { mutableStateOf(HistoryRange.Month) }
    val detail by
      remember(range) {
          repository.observeComponent(
            ComponentId(screen.componentId),
            clock.now() - range.days.days,
          )
        }
        .collectAsRetainedState(null)

    val eventSink: (ComponentEvent) -> Unit = { event ->
      when (event) {
        ComponentEvent.Back -> navigator.pop()
        is ComponentEvent.SelectRange -> range = event.range
      }
    }
    return ComponentUiState(detail?.toModel(range, clock.now()), screen.inSheet, eventSink)
  }
}
