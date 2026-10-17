plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
  id("cassette.di")
  alias(libs.plugins.sqldelight)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":core"))
      api(libs.sqldelight.runtime)
      implementation(libs.sqldelight.coroutines)
      implementation(libs.metro.runtime)
    }
    androidMain.dependencies { implementation(libs.sqldelight.android) }
    iosMain.dependencies { implementation(libs.sqldelight.native) }
    jvmMain.dependencies { implementation(libs.sqldelight.sqlite) }
    jvmTest.dependencies { implementation(libs.kotlin.test) }
  }
}

sqldelight {
  databases {
    create("CassetteDatabase") {
      packageName.set("com.r0adkll.cassette.data.db")
      schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
      // Every .sq change ships a .sqm migration; this checks they agree (decision 0007).
      verifyMigrations.set(true)
    }
  }
}
