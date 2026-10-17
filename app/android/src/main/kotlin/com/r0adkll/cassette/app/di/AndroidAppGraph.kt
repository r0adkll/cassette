package com.r0adkll.cassette.app.di

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import com.r0adkll.cassette.capture.api.CaptureController
import com.r0adkll.cassette.core.CassetteDispatchers
import com.r0adkll.cassette.data.ble.BackgroundDeliveryHandler
import com.r0adkll.cassette.data.db.createAndroidDriver
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
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

    @Provides
    @SingleIn(AppScope::class)
    fun provideCircuit(
        uiFactories: Set<Ui.Factory>,
        presenterFactories: Set<Presenter.Factory>,
    ): Circuit =
        Circuit.Builder()
            .addUiFactories(uiFactories)
            .addPresenterFactories(presenterFactories)
            .build()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSqlDriver(application: Application): SqlDriver = createAndroidDriver(application)

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
