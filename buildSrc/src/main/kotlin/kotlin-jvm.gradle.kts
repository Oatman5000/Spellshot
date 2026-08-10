package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(25)
}

group = "net.everlands"
version = "0.1.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        name = "Hytale"
        url = uri("https://maven.hytale.com/pre-release")
    }
    maven {
        name = "HytaleRelease"
        url = uri("https://maven.hytale.com/release")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}

