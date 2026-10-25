package com.r0adkll.cassette.wear.complication

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * What one complication on a face shows (decision 0027): a bike (null: the default bike) and a
 * component by its `ComponentKind` name (null: the weakest, or the one to charge).
 */
data class ComplicationPick(val bikeId: Long? = null, val kind: String? = null)

/** Each complication's pick, by the instance id the watch face gives it. */
interface ComplicationPicks {
  /** The pick for [complicationId]; null when it has none (a face's own default slot). */
  fun get(complicationId: Int): ComplicationPick?

  fun set(complicationId: Int, pick: ComplicationPick)

  /** The complication came off its face. */
  fun remove(complicationId: Int)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SharedPreferencesComplicationPicks(application: Application) : ComplicationPicks {
  private val prefs = application.getSharedPreferences("complications", Context.MODE_PRIVATE)

  override fun get(complicationId: Int): ComplicationPick? =
    if (!prefs.getBoolean(picked(complicationId), false)) null
    else
      ComplicationPick(
        bikeId = bike(complicationId).takeIf(prefs::contains)?.let { prefs.getLong(it, 0) },
        kind = prefs.getString(kind(complicationId), null),
      )

  override fun set(complicationId: Int, pick: ComplicationPick) {
    val edit = prefs.edit().putBoolean(picked(complicationId), true)
    if (pick.bikeId == null) edit.remove(bike(complicationId))
    else edit.putLong(bike(complicationId), pick.bikeId)
    edit.putString(kind(complicationId), pick.kind).apply()
  }

  override fun remove(complicationId: Int) {
    prefs
      .edit()
      .remove(picked(complicationId))
      .remove(bike(complicationId))
      .remove(kind(complicationId))
      .apply()
  }

  private fun picked(id: Int) = "picked_$id"

  private fun bike(id: Int) = "bike_$id"

  private fun kind(id: Int) = "kind_$id"
}
