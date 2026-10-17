package com.r0adkll.cassette.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** A screen module: multiplatform, Compose, Metro + Circuit, and the design system. */
class UiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            with(pluginManager) {
                apply("cassette.android.library")
                apply("cassette.multiplatform")
                apply("cassette.compose")
                apply("cassette.di")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets["commonMain"].dependencies {
                    implementation(project(":common:compose"))
                    implementation(project(":common:screens"))
                    implementation(libs.findLibrary("circuit-runtime").get())
                    implementation(libs.findLibrary("circuit-foundation").get())
                    implementation(libs.findLibrary("circuit-retained").get())
                    implementation(libs.findLibrary("circuit-codegen-annotations").get())
                    implementation(libs.findLibrary("metro-runtime").get())
                }
                sourceSets["commonTest"].dependencies {
                    implementation(libs.findLibrary("kotlin-test").get())
                    implementation(libs.findLibrary("circuit-test").get())
                    implementation(libs.findLibrary("turbine").get())
                    implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                }
            }
        }
}
