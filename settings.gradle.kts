pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.ornithemc.net/releases") { name = "Ornithe" }
        gradlePluginPortal()
    }
}

rootProject.name = "rago-client"
