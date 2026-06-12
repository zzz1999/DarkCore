rootProject.name = "EntityReskin"

// Centralized dependency repositories (modern Gradle: declare repos here, not in build scripts).
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        // Spigot API for the `server` plugin module (plus its transitive snapshot dependencies).
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

// `shared` is pure Java (protocol + manifest), consumed by both `server` and `client`.
// `web` is the Spring Boot resource backend (accounts, uploads, manifest + signed URLs).
// `server` is the Bukkit plugin (zero-NMS, one jar for 1.13 -> latest).
// `client` (Fabric mod, Stonecutter-managed) is added in a later phase.
include(":shared")
include(":web")
include(":server")
