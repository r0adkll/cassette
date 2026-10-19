package com.r0adkll.cassette.convention

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The last digit of a version code: the phone and Wear apps share a package, so Play needs both.
 */
enum class FormFactor(val digit: Int) {
  Phone(0),
  Wear(1),
}

/** The second to last digit, so no two build types of one version share a code. */
private val BUILD_TYPE_DIGITS = mapOf("release" to 0, "internal" to 1, "debug" to 2)

/**
 * Sets the app's version name from `cassette.version` (gradle.properties) and each variant's
 * version code from it with [versionCode]. Drop orders Builds by run, so internal builds of one
 * version can share a code.
 */
fun Project.configureVersions(formFactor: FormFactor) {
  val versionName = providers.gradleProperty("cassette.version").get()
  extensions.configure<ApplicationExtension> { defaultConfig.versionName = versionName }
  extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
      val buildType = variant.buildType ?: error("${variant.name} has no build type")
      val code = versionCode(versionName, buildType, formFactor)
      variant.outputs.forEach { it.versionCode.set(code) }
    }
  }
}

/**
 * `MMmmppRRTF`: major, minor, patch, then RR, the rc number or 99 for a final release (as in
 * Campfire), so 1.0.0-rc3 < 1.0.0 < 1.0.1-rc1. T is the build type and F the form factor, so a
 * version's phone and Wear bundles, and its release and internal builds, never share a code. Play's
 * ceiling (2,100,000,000) allows majors up to 20.
 */
fun versionCode(versionName: String, buildType: String, formFactor: FormFactor): Int {
  val match =
    SEMVER.matchEntire(versionName)
      ?: error("cassette.version '$versionName' isn't major.minor.patch[-rcN]")
  val (major, minor, patch, rc) = match.destructured
  require(major.toInt() <= 20) { "Version codes only fit majors up to 20" }
  require(minor.toInt() < 100 && patch.toInt() < 100) { "Minor and patch must be under 100" }
  require(rc.isEmpty() || rc.toInt() in 1..98) { "The rc number must be 1 to 98" }
  val buildTypeDigit =
    BUILD_TYPE_DIGITS[buildType] ?: error("No version code digit for the '$buildType' build type")
  val version =
    major.toInt() * 1_000_000 +
      minor.toInt() * 10_000 +
      patch.toInt() * 100 +
      (rc.toIntOrNull() ?: 99)
  return version * 100 + buildTypeDigit * 10 + formFactor.digit
}

private val SEMVER = """(\d+)\.(\d+)\.(\d+)(?:-rc(\d+))?""".toRegex()
