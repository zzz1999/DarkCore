// Root build script. No plugins applied at the root; each module configures itself.
// `shared` and `server` target Java 8 bytecode so a single server jar loads on the full
// 1.13 -> latest server range; `client` targets a current Java 21 Minecraft release.
allprojects {
    group = "io.github.zzz1999.entityreskin"
    version = "0.1.0-SNAPSHOT"
}
