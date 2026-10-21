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

/** The second to last digit of a Drop build's code, for its build type. */
private val DROP_BUILD_TYPE_DIGITS = mapOf("internal" to 1, "debug" to 2)

/**
 * Sets the app's version name from `cassette.version` (gradle.properties) and each variant's
 * version code: release's from the version ([releaseVersionCode]), the Drop builds' (debug and
 * internal, under the `.debug` id) from the commit count ([dropVersionCode]), so each Build of a
 * version has its own code.
 */
fun Project.configureVersions(formFactor: FormFactor) {
  val versionName = providers.gradleProperty("cassette.version").get()
  // First-parent only, as Drop counts: a pull request's merge commit never out-counts the main
  // commit its merge produces. Only run when a Drop build's code is needed.
  val commitCount =
    providers
      .exec { commandLine("git", "rev-list", "--count", "--first-parent", "HEAD") }
      .standardOutput
      .asText
      .map { it.trim().toInt() }
  extensions.configure<ApplicationExtension> { defaultConfig.versionName = versionName }
  extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
      val code =
        when (val buildType = extendedBuildType(variant.buildType.orEmpty())) {
          "release" -> providers.provider { releaseVersionCode(versionName, formFactor) }
          else -> commitCount.map { dropVersionCode(it, buildType, formFactor) }
        }
      variant.outputs.forEach { it.versionCode.set(code) }
    }
  }
}

/**
 * `MMmmpp0F`: major, minor and patch (as in Campfire, without its rc digits), a 0, then F, the form
 * factor, so a version's phone and Wear bundles never share a code and every code of a version is
 * below the next version's.
 */
fun releaseVersionCode(versionName: String, formFactor: FormFactor): Int {
  val match =
    SEMVER.matchEntire(versionName)
      ?: error("cassette.version '$versionName' isn't major.minor.patch")
  val (major, minor, patch) = match.destructured
  require(minor.toInt() < 100 && patch.toInt() < 100) { "Minor and patch must be under 100" }
  val version = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
  return version * 100 + formFactor.digit
}

/**
 * `<commits>TF`: the first-parent commit count, then T, the build type, and F, the form factor.
 * Drop builds have their own id, so these never meet release's codes.
 */
fun dropVersionCode(commitCount: Int, buildType: String, formFactor: FormFactor): Int {
  val buildTypeDigit =
    DROP_BUILD_TYPE_DIGITS[buildType]
      ?: error("No version code digit for the '$buildType' build type")
  return commitCount * 100 + buildTypeDigit * 10 + formFactor.digit
}

/**
 * The build type a baseline profile build type extends (`nonMinifiedRelease` and `benchmarkRelease`
 * are release's), so it takes that build type's code; any other build type is its own.
 */
fun extendedBuildType(buildType: String): String {
  val prefix =
    BASELINE_PROFILE_PREFIXES.firstOrNull { buildType.startsWith(it) && buildType != it }
      ?: return buildType
  return buildType.removePrefix(prefix).replaceFirstChar { it.lowercase() }
}

private val BASELINE_PROFILE_PREFIXES = listOf("nonMinified", "benchmark")

private val SEMVER = """(\d+)\.(\d+)\.(\d+)""".toRegex()
