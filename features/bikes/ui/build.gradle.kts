plugins { id("cassette.ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:bikes:api"))
            implementation(project(":features:capture:api"))
        }
    }
}
