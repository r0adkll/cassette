plugins { `kotlin-dsl` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    implementation(libs.metro.gradlePlugin)
    implementation(libs.ktfmt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("root") {
            id = "cassette.root"
            implementationClass = "com.r0adkll.cassette.convention.RootConventionPlugin"
        }
        register("multiplatform") {
            id = "cassette.multiplatform"
            implementationClass = "com.r0adkll.cassette.convention.KotlinMultiplatformConventionPlugin"
        }
        register("androidLibrary") {
            id = "cassette.android.library"
            implementationClass = "com.r0adkll.cassette.convention.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "cassette.android.application"
            implementationClass = "com.r0adkll.cassette.convention.AndroidApplicationConventionPlugin"
        }
        register("compose") {
            id = "cassette.compose"
            implementationClass = "com.r0adkll.cassette.convention.ComposeConventionPlugin"
        }
        register("di") {
            id = "cassette.di"
            implementationClass = "com.r0adkll.cassette.convention.DiConventionPlugin"
        }
        register("ui") {
            id = "cassette.ui"
            implementationClass = "com.r0adkll.cassette.convention.UiConventionPlugin"
        }
    }
}
