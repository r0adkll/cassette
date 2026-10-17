package com.r0adkll.cassette.wear

import android.app.Application
import com.r0adkll.cassette.wear.di.WearAppGraph
import dev.zacsweers.metro.createGraphFactory

class WearApplication : Application() {
  /** Android entry points reach the graph through this (decision 0007). */
  val graph: WearAppGraph by lazy { createGraphFactory<WearAppGraph.Factory>().create(this) }
}
