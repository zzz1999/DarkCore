plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    // Gson is provided at runtime by both Minecraft (client) and Bukkit (server),
    // so it is `api` here for compilation/tests but must NOT be shaded downstream.
    api(libs.gson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Java 8 bytecode: the shared protocol must load on the oldest supported servers (1.13-1.16
// run on Java 8) as well as modern Java 21 clients. Compiled with the JDK 21 toolchain via --release 8.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

// Published to the local Maven repository so the separately-built `client` mod (its own
// Fabric/Loom Gradle build, which needs a different Gradle/JDK than this backend build) can
// consume this version-independent protocol code as an ordinary dependency. Run
// `:shared:publishToMavenLocal`; the client then resolves io.github.zzz1999.entityreskin:shared from mavenLocal.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
