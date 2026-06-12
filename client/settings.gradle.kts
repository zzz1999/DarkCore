// The `client` mod is a SEPARATE Gradle build, not a subproject of the backend build.
// Rationale: a current Minecraft release (1.21.11) builds with Fabric Loom on Gradle 8.14 / JDK 21,
// whereas later Minecraft (26.x) requires Gradle 9.x / JDK 25 and the backend requires Spring Boot's
// own Gradle floor. Keeping the client build independent lets each pick the toolchain its target
// needs, and lets Loom manage its own repositories without the backend's locked-down resolution mode.
// Invoke with: gradlew -p client <task>
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "entityreskin-client"
