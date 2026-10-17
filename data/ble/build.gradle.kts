plugins {
    id("cassette.android.library")
    id("cassette.multiplatform")
    id("cassette.di")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.metro.runtime)
        }
        androidMain.dependencies { implementation(libs.androidx.core.ktx) }
    }
}
