plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.+"
    id("ploceus") version "1.17.+"
}

val minecraftVersion = property("minecraft_version") as String

group = property("maven_group") as String
version = "${property("mod_version")}+$minecraftVersion"
base.archivesName = "rago"

repositories {
    // Google's Maven Central mirror first: plain Maven Central rate-limits shared CI/cloud IPs (HTTP 429)
    maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    // OSL modules pulled in transitively (e.g. by legacy-lwjgl3)
    maven("https://maven.ornithemc.net/releases") { name = "Ornithe" }
    // LWJGL natives/classes come from Maven Central only (same approach as Argentum and DolphinClient)
    exclusiveContent {
        forRepository { maven("https://maven-central.storage-download.googleapis.com/maven2/") }
        filter { includeGroup("org.lwjgl") }
    }
    // legacy-lwjgl3 (lets 1.8.9 run on LWJGL3, required on Apple Silicon); published by its author here
    maven("https://maven.axolotlclient.com/releases") {
        content { includeGroupByRegex("io\\.github\\.moehreag(\\..*)?") }
    }
}

ploceus {
    setIntermediaryGeneration(2)
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(ploceus.featherMappings(property("feather_build") as String))

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    ploceus.dependOsl(property("osl_version") as String)

    // Dev runtime only: Rago itself does not depend on it. Users get it from the pack.
    modRuntimeOnly("io.github.moehreag:legacy-lwjgl3:${property("legacy_lwjgl3_version")}")
    // GL11.glGetString has the same signature in LWJGL 2 and 3, so compiling against 3 is safe for both
    compileOnly("org.lwjgl:lwjgl-opengl:${property("lwjgl_version")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

loom {
    runs.named("client") {
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            vmArg("-XstartOnFirstThread")
        }
        runDir = "run"
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
