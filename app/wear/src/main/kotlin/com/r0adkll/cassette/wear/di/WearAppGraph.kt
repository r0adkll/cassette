package com.r0adkll.cassette.wear.di

import android.app.Application
import com.r0adkll.cassette.wear.data.PhoneData
import com.r0adkll.cassette.wear.tile.TileRefresh
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

@DependencyGraph(AppScope::class)
interface WearAppGraph {
  val circuit: Circuit
  val phoneData: PhoneData
  val clock: Clock
  val tileRefresh: TileRefresh

  @Provides
  @SingleIn(AppScope::class)
  fun provideCircuit(
    uiFactories: Set<Ui.Factory>,
    presenterFactories: Set<Presenter.Factory>,
  ): Circuit =
    Circuit.Builder().addUiFactories(uiFactories).addPresenterFactories(presenterFactories).build()

  @Provides fun provideClock(): Clock = Clock.System

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): WearAppGraph
  }
}
