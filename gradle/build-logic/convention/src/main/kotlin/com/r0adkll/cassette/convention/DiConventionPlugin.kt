package com.r0adkll.cassette.convention

import dev.zacsweers.metro.gradle.MetroPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Metro, with its Circuit code generation for `@CircuitInject` presenters and UIs. */
class DiConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) =
    with(target) {
      pluginManager.apply("dev.zacsweers.metro")
      extensions.configure<MetroPluginExtension> { enableCircuitCodegen.set(true) }
    }
}
