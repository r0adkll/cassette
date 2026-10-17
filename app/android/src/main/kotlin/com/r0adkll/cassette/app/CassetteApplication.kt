package com.r0adkll.cassette.app

import android.app.Application
import com.r0adkll.cassette.app.di.AndroidAppGraph
import com.r0adkll.cassette.data.ble.BackgroundDeliveryHandler
import com.r0adkll.cassette.data.ble.BleHost
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CassetteApplication : Application(), BleHost {
    /** Android entry points reach the graph through this (decision 0007). */
    val graph: AndroidAppGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }

    override val bleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val backgroundDeliveryHandler: BackgroundDeliveryHandler
        get() = graph.backgroundDeliveryHandler

    override suspend fun rearmBackgroundCapture(reason: String) =
        graph.captureController.ensureBackgroundCapture(reason)
}
