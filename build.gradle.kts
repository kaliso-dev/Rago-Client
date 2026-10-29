import java.net.URI
import java.security.MessageDigest

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

    // compiled against for the Accounts screen; at run time the bundled copy is loaded as a mod
    compileOnly(files("libs/ias/InGameAccountSwitcher-Forge-1.8.9-7.1.2.jar"))

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

// In-Game Account Switcher (LGPL-3.0, see libs/ias/README.md) is bundled unmodified the same way;
// Rago's Accounts screen uses it for the Microsoft sign-in.
val iasJar = fileTree("libs/ias") { include("InGameAccountSwitcher*.jar") }.files.sortedBy { it.name }.lastOrNull()

// PolyPatcher (CC BY-NC-SA 4.0, see libs/patcher/README.md) is bundled unmodified the same way. It is
// downloaded from Modrinth at build time and checked against its published SHA-512, so the
// repository never stores it; -PnoPatcher builds without it.
val patcherName = "PolyPatcher-1.8.9-forge-1.10.4.jar"
val patcherUrl = "https://cdn.modrinth.com/data/YknNc5nN/versions/iNjGeSxM/$patcherName"
val patcherSha512 = "dc4979fce67d4e1c18143bb369ef0f597438a925baa47cea30aebb027b813a2520fe16e58f6eb4a55e80a8d22bfab86f100009e9a2b35fdd9d219564e300d0b4"
val patcherFile = layout.buildDirectory.file("patcher/$patcherName")
val bundlePatcher = !project.hasProperty("noPatcher")

val downloadPatcher by tasks.registering {
    onlyIf { bundlePatcher }
    outputs.file(patcherFile)
    doLast {
        val out = patcherFile.get().asFile
        fun sha512(f: File) = MessageDigest.getInstance("SHA-512").digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (out.isFile && sha512(out) == patcherSha512) {
            return@doLast
        }
        out.parentFile.mkdirs()
        val tmp = File(out.parentFile, "$patcherName.part")
        URI(patcherUrl).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val got = sha512(tmp)
        if (got != patcherSha512) {
            tmp.delete()
            throw GradleException("$patcherName from Modrinth has SHA-512 $got, expected $patcherSha512")
        }
        tmp.renameTo(out)
    }
}

// The jar is both a Forge mod and a coremod: the transformer raises the menu frame-rate cap
tasks.jar {
    manifest.attributes(
        "FMLCorePlugin" to "gg.rago.asm.RagoCorePlugin",
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true",
    )
    dependsOn(downloadPatcher)
    val patcherJar = if (bundlePatcher) patcherFile.get().asFile else null
    val contained = listOfNotNull(optifineJar, iasJar, patcherJar)
    contained.forEach { from(it) }
    if (patcherJar != null) {
        from("libs/patcher") {
            include("LICENSE.md", "README.md")
            into("META-INF/licenses/polypatcher")
        }
    }
    if (iasJar != null) {
        from("libs/ias") {
            include("LICENSE", "COPYING", "README.md")
            into("META-INF/licenses/in-game-account-switcher")
        }
    }
    if (contained.isNotEmpty()) {
        manifest.attributes("ContainedDeps" to contained.joinToString(" ") { it.name })
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
