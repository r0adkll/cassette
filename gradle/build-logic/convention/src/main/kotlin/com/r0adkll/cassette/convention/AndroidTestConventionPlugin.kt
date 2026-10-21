package com.r0adkll.cassette.convention

import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** A `com.android.test` module, such as `:app:baselineprofile`, at the apps' SDK levels. */
class AndroidTestConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) =
    with(target) {
      pluginManager.apply("com.android.test")

      extensions.configure<TestExtension> {
        namespace = computedNamespace
        compileSdk = Versions.COMPILE_SDK
        defaultConfig {
          minSdk = Versions.MIN_SDK
          targetSdk = Versions.TARGET_SDK
        }
      }

      configureJava()
    }
}
