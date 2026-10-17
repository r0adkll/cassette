package com.r0adkll.cassette.app.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Hosts Cassette's widgets at fixed sizes, for checking them without placing them on a launcher.
 * Needs bind permission once: `adb shell appwidget grantbind --package com.r0adkll.cassette.debug`.
 * Start with `adb shell am start -n
 * com.r0adkll.cassette.debug/com.r0adkll.cassette.app.debug.WidgetPreviewActivity`.
 */
class WidgetPreviewActivity : Activity() {
  private val host by lazy { AppWidgetHost(this, HOST_ID) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    host.deleteHost() // fresh ids each launch
    val column =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(48), dp(16), dp(48))
        setBackgroundColor(Color.rgb(0x2E, 0x2F, 0x35))
      }
    val manager = AppWidgetManager.getInstance(this)
    listOf(
        Triple(BIKE, "Small", 170 to 170),
        Triple(BIKE, "Medium", 358 to 170),
        Triple(BIKE, "Large", 358 to 358),
        Triple(ALL_BIKES, "All bikes", 358 to 170),
      )
      .forEach { (receiver, label, size) ->
        val id = host.allocateAppWidgetId()
        val provider = ComponentName(this, receiver)
        if (!manager.bindAppWidgetIdIfAllowed(id, provider)) {
          column.addView(
            label("Bind not allowed: run adb shell appwidget grantbind --package $packageName")
          )
          return@forEach
        }
        val (w, h) = size
        val options =
          Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            putParcelableArrayList(
              AppWidgetManager.OPTION_APPWIDGET_SIZES,
              arrayListOf(SizeF(w.toFloat(), h.toFloat())),
            )
          }
        manager.updateAppWidgetOptions(id, options)
        column.addView(label(label))
        val view = host.createView(this, id, manager.getAppWidgetInfo(id))
        column.addView(
          view,
          LinearLayout.LayoutParams(dp(w), dp(h)).apply { bottomMargin = dp(16) },
        )
      }
    setContentView(ScrollView(this).apply { addView(column) })
  }

  override fun onStart() {
    super.onStart()
    host.startListening()
  }

  override fun onStop() {
    host.stopListening()
    super.onStop()
  }

  private fun label(text: String) =
    TextView(this).apply {
      this.text = text
      setTextColor(Color.rgb(0xC9, 0xC9, 0xCF))
      setPadding(0, 0, 0, dp(6))
    }

  private fun dp(value: Int) =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
      )
      .toInt()

  private companion object {
    const val HOST_ID = 4_242
    const val BIKE = "com.r0adkll.cassette.surfaces.widget.BikeWidgetReceiver"
    const val ALL_BIKES = "com.r0adkll.cassette.surfaces.widget.AllBikesWidgetReceiver"
  }
}
