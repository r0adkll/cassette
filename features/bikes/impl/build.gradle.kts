plugins {
    id("cassette.android.library")
    id("cassette.multiplatform")
    id("cassette.di")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":features:bikes:api"))
            implementation(project(":data:db"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.metro.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
