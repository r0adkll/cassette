package com.r0adkll.cassette.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

object Versions {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 31
    const val TARGET_SDK = 36
    const val JAVA = 21
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** `:common:compose` becomes `com.r0adkll.cassette.common.compose`. */
internal val Project.computedNamespace: String
    get() = "com.r0adkll.cassette." + path.removePrefix(":").replace(':', '.').replace('-', '_')

internal fun Project.configureJava() {
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(Versions.JAVA)) }
    }
}
