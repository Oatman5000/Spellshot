plugins {
    id("java-library")
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.springDependencyManagement)
}
group = "net.everlands"
version = "0.3.0.0"
dependencies {
    api("org.slf4j:slf4j-api:2.0.9")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    compileOnly(libs.hytaleServer)
    api(libs.springBoot)
    api("org.springframework:spring-context")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.mongodb:bson:5.2.1")
    // Leaderboard transport between the game server and the lobby.
    // api(), not implementation(): both moshpit and lobby shade :core into their
    // own jars and call RedisClient directly, so the classes have to be exported.
    api("redis.clients:jedis:5.2.0")
    // @Description on the Redis/leaderboard configs. moshpit already had this;
    // core did not until these configs moved here.
    api("io.github.smiley4:schema-kenerator-core:2.3.0")
}
