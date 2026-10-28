pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.ornithemc.net/releases") { name = "Ornithe" }
        gradlePluginPortal()
    }
}

plugins {
    // Downloads the Java 25 runtime Argentum needs for runClient when it is not installed
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "rago-client"
