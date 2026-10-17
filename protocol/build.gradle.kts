plugins {
    id("cassette.android.library")
    id("cassette.multiplatform")
}

kotlin { sourceSets { commonTest.dependencies { implementation(libs.kotlin.test) } } }
