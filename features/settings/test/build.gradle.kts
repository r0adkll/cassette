plugins {
  id("cassette.android.library")
  id("cassette.multiplatform")
}

kotlin { sourceSets { commonMain.dependencies { api(project(":features:settings:api")) } } }
