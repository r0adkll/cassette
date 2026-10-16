package com.r0adkll.cassette.axs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The table below is a real read of `d9050003-…` from a Red AXS rear derailleur (HCI snoop). */
class SystemBatteryTableTest {
  private val redTable =
    bytes(
      "01 04 6e 7a c1 53 80 ea 03 cb 66 63 4e 03 97 1f 00 00 00 00 00 00 " +
        "00 00 00 00 d0 07 00 00 01 ec 03 f2 fe 7a 43 03 78 0b 00 00 00 00 " +
        "00 00 00 00 00 00 00 00 00 00 00 ed 03 31 2e 7b 43 03 82 0b 00 00 " +
        "00 00 00 00 00 00 00 00 00 00 00 00 81 2a 04 6e 7a c1 53 03 cc 1e " +
        "00 00 00 00 00 00 d2 42 ed f3 24 30 00 00"
    )

  @Test
  fun decodesEveryComponentInRedTable() {
    val table = SystemBatteryTable.parse(redTable)!!

    assertEquals(1405188718L, table.masterSerial)
    assertEquals(
      listOf(1315137227L to 8087, 1132134130L to 2936, 1132146225L to 2946, 1405188718L to 7884),
      table.rows.map { it.serial to it.millivolts },
    )
    assertEquals(listOf(1002, 1004, 1005, 1066), table.rows.map { it.modelId })
    assertEquals(
      listOf(ComponentKind.FRONT_DERAILLEUR, ComponentKind.RIGHT_SHIFTER, ComponentKind.LEFT_SHIFTER, ComponentKind.REAR_DERAILLEUR),
      table.rows.map { it.kind },
    )
    assertEquals(List(4) { BatteryStatus.OK }, table.rows.map { it.reportedStatus })
    assertEquals(12_324L, table.rows.last().actuations)
  }

  @Test
  fun decodesFirmwareTable() {
    val versions =
      SystemBatteryTable.parseVersions(
        bytes(
          "02 04 6e 7a c1 53 81 2a 04 6e 7a c1 53 00 08 37 02 4c 01 04 01 01 " +
            "80 ea 03 cb 66 63 4e 00 03 07 02 00 00 00 00 00 01 ec 03 f2 fe 7a " +
            "43 00 03 07 02 00 00 00 00 ff 00 ed 03 31 2e 7b 43 00 03 07 02 00 " +
            "00 00 00 ff"
        )
      )!!

    assertEquals(1405188718L, versions.masterSerial)
    assertEquals(listOf("2.55.8", "2.7.3", "2.7.3", "2.7.3"), versions.rows.map { it.firmware })
    assertEquals(332, versions.rows.first().softDevice)
    assertEquals("1.4", versions.rows.first().bootloader)
  }

  @Test
  fun rejectsOtherTablesAndTruncatedReads() {
    assertNull(SystemBatteryTable.parse(bytes("02 04 6e 7a c1 53")))
    assertNull(SystemBatteryTable.parse(redTable.copyOf(50)))
  }

  @Test
  fun estimatesPackPercentFromObservedPoints() {
    assertEquals(81, estimatePackPercent(7_881))
    assertEquals(100, estimatePackPercent(8_200))
    assertEquals(19, estimatePackPercent(7_479))
    assertEquals(0, estimatePackPercent(6_000))
    assertEquals(95, estimatePackPercent(8_060))
  }

  private fun bytes(hex: String) = hex.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
