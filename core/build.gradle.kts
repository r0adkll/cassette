plugins {
    id("cassette.android.library")
    id("cassette.multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies { api(project(":protocol")) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
