package com.r0adkll.cassette.app.di

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import com.r0adkll.cassette.alerts.api.Alerts
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.capture.impl.CaptureLogFile
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.BackgroundDeliveryHandler
import com.r0adkll.cassette.data.db.createAndroidDriver
import com.r0adkll.cassette.settings.api.CassetteSettings
import com.r0adkll.cassette.settings.impl.SettingsFile
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers

@DependencyGraph(AppScope::class)
interface AndroidAppGraph {
  val circuit: Circuit
  val captureController: CaptureController
  val backgroundDeliveryHandler: BackgroundDeliveryHandler
  val alerts: Alerts
  val settings: CassetteSettings

  @Provides
  @SingleIn(AppScope::class)
  fun provideCircuit(
    uiFactories: Set<Ui.Factory>,
    presenterFactories: Set<Presenter.Factory>,
  ): Circuit =
    Circuit.Builder()
      .addUiFactories(uiFactories)
      .addPresenterFactories(presenterFactories)
      // Android's predictive back: the screen underneath shows as you swipe.
      .setAnimatedNavDecoratorFactory(GestureNavigationDecorationFactory())
      .build()

  @Provides
  @SingleIn(AppScope::class)
  fun provideSqlDriver(application: Application): SqlDriver = createAndroidDriver(application)

  @Provides
  fun provideCaptureLogFile(application: Application): CaptureLogFile =
    CaptureLogFile(application.filesDir.resolve("capture-log.jsonl").absolutePath)

  @Provides
  fun provideSettingsFile(application: Application): SettingsFile =
    SettingsFile(application.filesDir.resolve("settings.preferences_pb").absolutePath)

  @Provides
  @SingleIn(AppScope::class)
  fun provideDispatchers(): CassetteDispatchers =
    CassetteDispatchers(
      io = Dispatchers.IO,
      default = Dispatchers.Default,
      main = Dispatchers.Main,
    )

  @Provides fun provideClock(): Clock = Clock.System

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): AndroidAppGraph
  }
}
