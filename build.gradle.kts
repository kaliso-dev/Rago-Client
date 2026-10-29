plugins {
    java
    id("gg.essential.loom") version "1.6.21"
    id("dev.architectury.architectury-pack200") version "0.1.3"
}

version = "${property("mod_version")}+${property("minecraft_version")}"
group = property("maven_group") as String
base.archivesName = "rago"

// Forge 1.8.9 runs on Java 8 only (LaunchWrapper), so Rago is compiled for and run on Java 8.
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    runConfigs {
        remove(getByName("server"))
        named("client") {
            runDir = "run"
            // Load Rago's class transformer in the dev client too (a normal install reads it from the jar manifest)
            property("fml.coreMods.load", "gg.rago.asm.RagoCorePlugin")
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
    }
}

// Forge 1.8.9 looks for assets next to the classes
sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

repositories {
    // Maven Central rate-limits CI and cloud machines; the Google mirror serves the same files.
    maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("de.oceanlabs.mcp:mcp_stable:${property("mcp_mappings")}")
    forge("net.minecraftforge:forge:${property("forge_version")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// runClient and tests must use Java 8 even when Gradle itself runs on a newer JDK
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
}

// OptiFine is bundled only when its jar is placed in libs/optifine/ (git ignores that folder,
// so the repository never contains OptiFine). Forge extracts a jar listed in ContainedDeps to
// mods/1.8.9/ on first launch and loads it like any other mod.
val optifineJar = fileTree("libs/optifine") { include("OptiFine*.jar") }.files.sortedBy { it.name }.lastOrNull()

// The jar is both a Forge mod and a coremod: the transformer raises the menu frame-rate cap
tasks.jar {
    manifest.attributes(
        "FMLCorePlugin" to "gg.rago.asm.RagoCorePlugin",
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true",
    )
    if (optifineJar != null) {
        from(optifineJar)
        manifest.attributes("ContainedDeps" to optifineJar.name)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") {
        expand("version" to project.version)
    }
}
