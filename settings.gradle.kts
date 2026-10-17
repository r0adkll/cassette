pluginManagement {
  includeBuild("gradle/build-logic")
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

rootProject.name = "Cassette"

include(":app:android")

include(":common:compose")

include(":common:screens")

include(":core")

include(":data:ble")

include(":data:db")

include(":features:alerts:api")

include(":features:alerts:impl")

include(":features:bikes:api")

include(":features:capture:api")

include(":features:capture:impl")

include(":features:diagnostics:ui")

include(":features:onboarding:ui")

include(":features:settings:api")

include(":features:settings:impl")

include(":features:settings:test")

include(":features:settings:ui")

include(":features:bikes:impl")

include(":features:bikes:ui")

include(":protocol")

include(":surfaces:tile")

include(":surfaces:widget")

// The proof of concept, kept installable (and its diagnostics usable) until features are ported.
include(":poc")
