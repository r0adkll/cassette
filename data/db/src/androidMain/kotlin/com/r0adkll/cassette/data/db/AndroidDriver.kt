package com.r0adkll.cassette.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

fun createAndroidDriver(context: Context, name: String = DATABASE_NAME): SqlDriver =
  AndroidSqliteDriver(
    schema = CassetteDatabase.Schema,
    context = context,
    name = name,
    callback =
      object : AndroidSqliteDriver.Callback(CassetteDatabase.Schema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
          db.setForeignKeyConstraintsEnabled(true)
        }
      },
  )
