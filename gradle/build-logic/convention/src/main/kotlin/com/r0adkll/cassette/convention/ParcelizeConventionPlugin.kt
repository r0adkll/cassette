package com.r0adkll.cassette.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Parcelize for shared code: `com.r0adkll.cassette.core.parcelize.Parcelize` counts as `@Parcelize`
 * on Android and is a no-op elsewhere. Circuit screens are Parcelable on Android.
 */
class ParcelizeConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) =
    with(target) {
      pluginManager.apply("org.jetbrains.kotlin.plugin.parcelize")

      extensions.configure<KotlinMultiplatformExtension> {
        targets.configureEach {
          if (platformType != KotlinPlatformType.androidJvm) return@configureEach
          compilations.configureEach {
            compileTaskProvider.configure {
              compilerOptions {
                freeCompilerArgs.addAll(
                  "-P",
                  "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=" +
                    "com.r0adkll.cassette.core.parcelize.Parcelize",
                )
              }
            }
          }
        }
      }
    }
}
