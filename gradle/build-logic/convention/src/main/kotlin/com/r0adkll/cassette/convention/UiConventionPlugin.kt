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
                    implementation(libs.findLibrary("circuit-runtime").get())
                    implementation(libs.findLibrary("circuit-foundation").get())
                }
            }
        }
}
