plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.springDependencyManagement)
}
group = "net.everlands"
version = "0.1.0"
dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-core")
    implementation("org.springframework:spring-aop")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    compileOnly("com.hypixel.hytale:Server:0.5.7")
    implementation(project(":core"))
    implementation("io.github.smiley4:schema-kenerator-core:2.3.0")
    implementation("it.unimi.dsi:fastutil:8.5.15")
    implementation("org.mongodb:bson:5.2.1")
}
tasks.shadowJar {
    relocate("com.google.common", "net.everlands.shaded.guava")
    archiveFileName.set("lobby-plugin.jar")
    // Bundle kotlin-stdlib, exclude kotlin-reflect. Both halves matter, and the
    // two runtimes pull in opposite directions:
    //
    //   Standalone (AMP): HytaleServer.jar contains no kotlin/ package at all,
    //     so without a bundled stdlib the plugin dies at boot with
    //     NoClassDefFoundError: kotlin/jvm/internal/Intrinsics.
    //
    //   Gradle dev-server: kotlin-stdlib IS on the app classpath. The plugin
    //     loader is parent-first, so stdlib resolves from 'app' while a bundled
    //     kotlin-reflect resolves from the plugin loader. Spring's Kotlin path
    //     then fails with
    //     ClassCastException: ClassReference cannot be cast to KClassImpl.
    //
    // Excluding reflect satisfies both: stdlib is present either way, and with
    // no reflect on the classpath Spring falls back to Java constructor
    // resolution. Our own code only touches KClass and KMutableProperty1, which
    // are stdlib interfaces -- nothing here needs the reflect implementation.
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
    }
    from(sourceSets.main.get().output)
    mergeServiceFiles()
}
tasks.build { dependsOn(tasks.shadowJar) }
