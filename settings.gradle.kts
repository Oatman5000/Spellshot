dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories { mavenCentral() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "spellshot"
include("core")
include("moshpit")
include("lobby")
