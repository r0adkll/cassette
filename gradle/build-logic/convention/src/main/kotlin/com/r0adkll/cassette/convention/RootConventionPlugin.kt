package com.r0adkll.cassette.convention

import com.ncorti.ktfmt.gradle.KtfmtExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applies ktfmt (KotlinLang style) to every project. */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.allprojects {
            pluginManager.apply("com.ncorti.ktfmt.gradle")
            extensions.configure<KtfmtExtension> { kotlinLangStyle() }
        }
    }
}
