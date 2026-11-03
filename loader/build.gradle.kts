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

tasks.jar {
    manifest.attributes("Main-Class" to "gg.rago.loader.Main", "Implementation-Version" to project.version)
    from(shade.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class", "META-INF/versions/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
