package com.r0adkll.cassette.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/** An in-memory database with the schema created; for tests. */
fun createInMemoryDriver(): SqlDriver =
  JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
    .also { CassetteDatabase.Schema.create(it) }
