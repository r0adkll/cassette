plugins {
    id("cassette.android.application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.r0adkll.cassette.app"

    defaultConfig {
        applicationId = "com.r0adkll.cassette"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        // The POC installs as com.r0adkll.cassette; the suffix lets both live on a phone.
        debug { applicationIdSuffix = ".debug" }
    }

    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":common:compose"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
