package com.r0adkll.cassette.surfaces.widget

import android.content.ComponentName
import android.content.Context

/** The widget providers, for code outside this module that shouldn't see Glance types. */
object CassetteWidgets {
  fun bike(context: Context) = ComponentName(context, BikeWidgetReceiver::class.java)

  fun all(context: Context) =
    listOf(bike(context), ComponentName(context, AllBikesWidgetReceiver::class.java))
}
