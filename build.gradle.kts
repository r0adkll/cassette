plugins {
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlin.parcelize) apply false
  alias(libs.plugins.metro) apply false
  alias(libs.plugins.sqldelight) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.firebase.crashlytics) apply false
}

tasks.register<Copy>("bootstrap") {
  description = "Installs the git pre-push hook (scripts/ktfmt)."
  from(file("scripts/pre-push"))
  into(file(".git/hooks"))
}
