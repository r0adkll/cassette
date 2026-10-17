package com.r0adkll.cassette.app

import android.app.Application
import com.r0adkll.cassette.app.di.AndroidAppGraph
import dev.zacsweers.metro.createGraphFactory

class CassetteApplication : Application() {
    /** Android entry points reach the graph through this (decision 0007). */
    val graph: AndroidAppGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }
}
