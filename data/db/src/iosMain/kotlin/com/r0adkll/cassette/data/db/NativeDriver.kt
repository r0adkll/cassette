package com.r0adkll.cassette.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration

fun createNativeDriver(name: String = DATABASE_NAME): SqlDriver =
    NativeSqliteDriver(
        schema = CassetteDatabase.Schema,
        name = name,
        onConfiguration = { config ->
            config.copy(
                extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true)
            )
        },
    )
