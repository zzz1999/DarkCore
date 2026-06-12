plugins {
    // Loom 1.15.4 builds Minecraft 1.21.11 on Gradle 9.4 / JDK 21 and, crucially, can consume
    // GeckoLib 5.4.5 — which was itself built with Loom 1.15.4, and an older Loom refuses a
    // dependency built with a newer one. (Only the 1.16+/26.x line additionally requires JDK 25.)
    id("fabric-loom") version "1.15.4"
}

group = "io.github.zzz1999.entityreskin"
version = "0.1.0-SNAPSHOT"

base {
    // Publish the mod jar as EntityReskin-client-<version>.jar (consistent with EntityReskin-server).
    archivesName.set("EntityReskin-client")
}

// Toolchain anchor (verified June 2026 against the Fabric metadata service and Modrinth):
//   Minecraft 1.21.11 (last Yarn/obfuscated release; needs JDK 21, which this machine has)
//   Fabric Loader 0.19.3 (current stable, version-independent)
//   Fabric API   0.141.4+1.21.11
//   GeckoLib     5.4.5 (geckolib-fabric-1.21.11, from Cloudsmith)
val minecraftVersion = "1.21.11"
val loaderVersion = "0.19.3"
val fabricApiVersion = "0.141.4+1.21.11"
val geckolibVersion = "5.4.5"
val sharedVersion = "0.1.0-SNAPSHOT"

repositories {
    // Version-independent protocol/manifest code, published by the backend build via
    // `:shared:publishToMavenLocal`.
    mavenLocal()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    // GeckoLib (Bedrock-format geometry/animation rendering).
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") { name = "GeckoLib" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // Mojang's official mappings: 1.21.11 still ships Yarn, but Mojmap is forward-compatible with
    // the unobfuscated 26.x line, so the client uses one mapping convention across all future targets.
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    // Bedrock-format geometry/animation rendering. Bundled into the mod jar via Loom jar-in-jar
    // so players install only EntityReskin, not GeckoLib separately. GeckoLib is MIT-licensed, which
    // permits redistribution; the nested jar carries its own LICENSE and copyright notice, which
    // satisfies the MIT attribution condition. Fabric API is deliberately NOT bundled: it is the
    // universal, independently-updated base mod, and bundling it would cause version conflicts.
    modImplementation("software.bernie.geckolib:geckolib-fabric-$minecraftVersion:$geckolibVersion")
    include("software.bernie.geckolib:geckolib-fabric-$minecraftVersion:$geckolibVersion")

    // Shared with the server plugin; contains no Minecraft references, so it is not remapped.
    // Bundled into the mod jar via Loom's jar-in-jar so PacketCodec is present at runtime.
    implementation("io.github.zzz1999.entityreskin:shared:$sharedVersion")
    include("io.github.zzz1999.entityreskin:shared:$sharedVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
