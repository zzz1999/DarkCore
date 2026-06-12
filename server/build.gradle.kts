plugins {
    `java-library`
}

base {
    // Publish the plugin jar as EntityReskin-server-<version>.jar instead of the Gradle project name.
    archivesName.set("EntityReskin-server")
}

// The plugin jar bundles the `shared` protocol classes so server owners install a single file.
// Gson is provided by every Bukkit server since 1.8 and is deliberately NOT bundled.
val bundledShared: Configuration by configurations.creating

dependencies {
    // bungeecord-chat is excluded: its pinned snapshot was purged from the public
    // repositories, and this plugin does not use the BungeeCord chat API.
    compileOnly(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
    implementation(project(":shared"))
    bundledShared(project(":shared")) { isTransitive = false }

    testImplementation(libs.spigot.api) { exclude(group = "net.md-5", module = "bungeecord-chat") }
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Java 8 bytecode: the single plugin jar must load on the full 1.13 -> latest server range.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.jar {
    // `elements` is a Provider and carries the task dependency on :shared:jar.
    from(bundledShared.elements.map { jars -> jars.map { zipTree(it.asFile) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
