// Rago's own loader: runs Minecraft 1.8.9 and Rago on a modern Java, without Forge.
plugins {
    java
}

group = "gg.rago"
version = "0.1.0"

repositories {
    // Maven Central rate-limits CI and cloud machines; the Google mirror serves the same files.
    maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    mavenCentral()
}

val shade: Configuration by configurations.creating
configurations.implementation { extendsFrom(shade) }

// The Forge-compatible API that mods call, and the hooks the game calls. It is compiled against the
// game renamed once by the loader's own `remap` command (MCP class names, SRG member names) and its
// libraries, which cannot be published: pass -PgameJar=<renamed jar> -PgameLibs=<libraries folder>.
// None of those classes end up in the loader jar, only the compatibility classes.
val gameJar = providers.gradleProperty("gameJar")
val gameLibs = providers.gradleProperty("gameLibs")
val compat: SourceSet = sourceSets.create("compat")

dependencies {
    if (gameJar.isPresent) {
        "compatCompileOnly"(files(gameJar.get()))
        "compatCompileOnly"(fileTree(gameLibs.get()) { include("**/*.jar") })
    }
}

dependencies {
    // BSD-3-Clause (ASM) and public domain (XZ for Java): both bundled in the loader jar
    shade("org.ow2.asm:asm:9.6")
    shade("org.ow2.asm:asm-tree:9.6")
    shade("org.ow2.asm:asm-commons:9.6")
    shade("org.tukaani:xz:1.9")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaCompile>("compileCompatJava") {
    onlyIf("needs -PgameJar and -PgameLibs") { gameJar.isPresent && gameLibs.isPresent }
}

tasks.jar {
    if (gameJar.isPresent) {
        from(compat.output)
    } else {
        doFirst { logger.warn("Built without the compatibility classes: the jar can rename the game, not start it (-PgameJar)") }
    }
    manifest.attributes("Main-Class" to "gg.rago.loader.Main", "Implementation-Version" to project.version)
    from(shade.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class", "META-INF/versions/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
