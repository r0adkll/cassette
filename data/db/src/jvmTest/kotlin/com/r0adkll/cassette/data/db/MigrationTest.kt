package com.r0adkll.cassette.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Upgrades from the committed schema snapshots, with rows a real install would have. */
class MigrationTest {
  private val file = File.createTempFile("cassette", ".db")
  private val driver by lazy {
    JdbcSqliteDriver("jdbc:sqlite:${file.path}", Properties().apply { put("foreign_keys", "true") })
  }

  @AfterTest
  fun close() {
    driver.close()
    file.delete()
  }

  @Test
  fun eachBikesHubBecomesItsFirstSystem() {
    File("src/commonMain/sqldelight/databases/3.db").copyTo(file, overwrite = true)
    driver.execute(
      null,
      "INSERT INTO bike (id, name, type, hub_serial, created_at, hub_read_at) VALUES " +
        "(1, 'Mountain bike', 'Mtb', 1121114092, 100, 200), " +
        // The sample bike's made-up hub.
        "(2, 'Sample bike', 'Road', -1, 300, NULL)",
      0,
    )
    driver.execute(
      null,
      "INSERT INTO component (id, bike_id, key, serial, kind, added_at) VALUES " +
        "(1, 1, '1121114092', 1121114092, 'DROPPER_POST', 100), " +
        "(2, 1, '1151498210', 1151498210, 'FORK', 100), " +
        "(3, 1, 'Quarq AGD60899', NULL, 'POWER_METER', 100), " +
        "(4, 2, 'sample:1405188718', 1405188718, 'REAR_DERAILLEUR', 300)",
      0,
    )

    CassetteDatabase.Schema.migrate(driver, 3, CassetteDatabase.Schema.version)
    val db = CassetteDatabase(driver)

    val hub = db.hubQueries.selectForBike(1).executeAsList().single()
    assertEquals(1121114092L, hub.serial)
    assertEquals(100L, hub.added_at)
    assertEquals(200L, hub.read_at)
    assertEquals(emptyList(), db.hubQueries.selectForBike(2).executeAsList())
    val systems =
      db.componentQueries.selectForBike(1).executeAsList().associate {
        it.key to it.hub_serial
      }
    assertEquals(1121114092L, systems["1121114092"])
    assertEquals(1121114092L, systems["1151498210"])
    assertNull(systems["Quarq AGD60899"])
  }
}
