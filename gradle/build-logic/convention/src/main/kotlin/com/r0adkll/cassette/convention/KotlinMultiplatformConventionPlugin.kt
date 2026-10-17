package com.r0adkll.cassette.convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * Android, iOS (device + simulator) and JVM. JVM is for fast tests only; there is no desktop app.
 * Apply `cassette.android.library` first to get the Android target.
 */
class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")

            extensions.configure<KotlinMultiplatformExtension> {
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-opt-in=kotlin.time.ExperimentalTime",
                        "-Xexpect-actual-classes",
                    )
                }

                applyDefaultHierarchyTemplate()

                jvm()

                if (pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")) {
                    extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("android") {
                        namespace = computedNamespace
                        compileSdk = Versions.COMPILE_SDK
                        minSdk = Versions.MIN_SDK
                        // Compose Multiplatform resources need Android resources enabled.
                        androidResources { enable = true }
                        // Compose and coroutines touch android.util.Log/Trace in host tests.
                        withHostTest { isReturnDefaultValues = true }
                    }
                }

                iosArm64()
                iosSimulatorArm64()

                targets.withType<KotlinNativeTarget>().configureEach {
                    binaries.framework { baseName = path.removePrefix(":").replace(':', '_') }
                }
            }

            configureJava()
        }
}
