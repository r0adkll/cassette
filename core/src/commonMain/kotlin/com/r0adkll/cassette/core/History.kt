package com.r0adkll.cassette.core

/**
 * When batteries were charged (packs) or replaced (coin cells), from jumps in level between
 * consecutive readings. [history] is oldest first. Readings without a level are skipped.
 */
fun charges(history: List<BatteryReading>, minJump: Float = CHARGE_JUMP): List<BatteryReading> =
  history
    .filter { it.millivolts != null || it.percent != null }
    .zipWithNext()
    .filter { (before, after) -> after.level - before.level >= minJump }
    .map { (_, after) -> after }

/** A quarter of the bar: well above day-to-day noise (temperature, voltage sag under load). */
const val CHARGE_JUMP = 0.25f
