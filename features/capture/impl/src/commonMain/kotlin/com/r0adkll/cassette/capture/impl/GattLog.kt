package com.r0adkll.cassette.capture.impl

import com.r0adkll.cassette.capture.api.GattOutcome
import com.r0adkll.cassette.data.ble.GattResult

/** A read's outcome for the capture log; [parsed] is whether it held what was asked for. */
internal fun GattResult.outcome(parsed: Boolean): GattOutcome =
  when {
    this is GattResult.Failed -> GattOutcome.Failed
    !parsed -> GattOutcome.Unsupported
    else -> GattOutcome.Ok
  }

/** Why a read failed, or the services a device listed when it answered without what was asked. */
internal fun GattResult.detail(parsed: Boolean): String? =
  when (this) {
    is GattResult.Failed -> reason
    is GattResult.Values ->
      if (parsed) null else "services: ${services.joinToString().ifEmpty { "none" }}"
  }
