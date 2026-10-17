package com.r0adkll.cassette.data.db

import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** The platform graph provides the [SqlDriver]; everything else gets the database from here. */
@ContributesTo(AppScope::class)
interface DatabaseProviders {
  @Provides
  @SingleIn(AppScope::class)
  fun provideDatabase(driver: SqlDriver): CassetteDatabase = CassetteDatabase(driver)
}
