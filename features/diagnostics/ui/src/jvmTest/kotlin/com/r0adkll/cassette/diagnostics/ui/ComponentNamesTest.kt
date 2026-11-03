package com.r0adkll.cassette.diagnostics.ui

import com.r0adkll.cassette.core.BikeId
import com.r0adkll.cassette.core.Component
import com.r0adkll.cassette.core.ComponentId
import com.r0adkll.cassette.protocol.ComponentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ComponentNamesTest {
  @Test
  fun serialLessFramesAreNamedByAddressWithTheBikeScreensCodes() {
    // A tester's ride test listed their fork's address as "?" and its badge read "Fork".
    val names =
      componentNames(
        listOf(
          component("1151498210", ComponentKind.FORK, "CA:FC:68:09:2D:96"),
          component("1121114092", ComponentKind.DROPPER_POST, "E2:E5:15:02:7D:74"),
          component("1013571428", ComponentKind.REAR_SHOCK, null),
        )
      )

    assertEquals(ComponentName("FK", "Fork"), names["CA:FC:68:09:2D:96"])
    assertEquals(ComponentName("FK", "Fork"), names["1151498210"])
    assertEquals(ComponentName("DP", "Dropper post"), names["E2:E5:15:02:7D:74"])
    assertEquals(ComponentName("SH", "Rear shock"), names["1013571428"])
    assertEquals(5, names.size)
  }

  private fun component(key: String, kind: ComponentKind, address: String?) =
    Component(
      id = ComponentId(0),
      bikeId = BikeId(1),
      key = key,
      serial = key.toLong(),
      kind = kind,
      modelId = null,
      deviceType = null,
      firmware = null,
      actuations = null,
      addedAt = Instant.fromEpochMilliseconds(0),
      lastSeenAt = null,
      removedAt = null,
      address = address,
    )
}
